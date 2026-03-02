package com.agentengine.engine.agents.flows;

import com.agentengine.engine.agents.processors.Parser;
import com.agentengine.engine.agents.processors.request.*;
import com.agentengine.engine.agents.processors.response.*;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.flows.llmflows.SingleFlow;
import com.google.adk.models.LlmRequest;
import com.google.common.collect.ImmutableList;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates scene generation through six sequential, isolated LLM round-trips.
 *
 * <p>Each phase is a single-turn call with its own focused prompt. Intermediate outputs accumulate
 * in the session history and are visible to later phases, except for Phase 2 (other-character
 * identification), which deliberately sees only the original user input so the protagonist profile
 * from Phase 1 does not bias the character-count inference.
 */
public final class StoryFlow extends DefaultFlow {
    private static final Logger LOG = LoggerFactory.getLogger(StoryFlow.class);

    // ---- Phase prompts ---------------------------------------------------------

    /**
     * Who is the central figure? Name, personality, and key traits only — no scene yet.
     */
    private static final String PHASE_1_PROTAGONIST =
            """
                    You are building a scene. Your first task is to define the protagonist.

                    Based solely on the user's description, infer or assume:
                    - The protagonist's name
                    - Their core personality traits
                    - Any defining characteristics relevant to the scene

                    Output a concise protagonist profile. Do not write the scene yet.""";

    /**
     * Who else is in the scene? Decided independently from the protagonist profile.
     * History-stripped so Phase 1's output is not visible here.
     */
    private static final String PHASE_2_OTHER_CHARACTERS =
            """
                    You are building a scene. Your task is to decide who else appears alongside the protagonist.

                    Based on the user's description, infer or assume the number and names of all secondary characters (everyone except the protagonist).

                    Output a single line in this exact format:
                    NAMES: <comma-separated character names>

                    Then briefly justify each character's presence. Do not write the scene yet.""";

    /**
     * Template for profiling each secondary character. Protagonist context is visible here.
     */
    private static final String PHASE_3_CHARACTER_PROFILE_TEMPLATE =
            """
                    You are building a scene. You have already established the protagonist and the cast.

                    Now focus on: %s

                    Based on the user's description and the protagonist's profile, infer or assume:
                    - Their personality and temperament
                    - Their relationship to the protagonist
                    - Any relevant backstory or motivation

                    Output a concise character profile. Do not write the scene yet.""";

    /**
     * What underpins the scene emotionally or thematically?
     */
    private static final String PHASE_4_THEME =
            """
                    You are building a scene. The protagonist and all secondary characters have been established.

                    Now determine the central theme or atmosphere:
                    - What emotion or idea does this scene revolve around?
                    - What tone does it carry (e.g. tension, warmth, dread, longing)?

                    Output a concise theme statement. Do not write the scene yet.""";

    /**
     * What actually happens — the concrete situation or conflict?
     */
    private static final String PHASE_5_SITUATION =
            """
                    You are building a scene. Characters and theme are established.

                    Now determine the specific situation or conflict:
                    - What is happening at the moment the scene begins?
                    - What triggers or drives the action?
                    - What is at stake?

                    Be concrete and specific. Do not write the scene yet.""";

    /**
     * Final synthesis — write the scene narrative in mixed perspective:
     * protagonist is narrated in first person, all other characters in third person.
     */
    private static final String PHASE_6_SCENE =
            "You have everything you need: characters, profiles, theme, and situation.\n\n" +
            "Now write the scene. Follow these perspective rules strictly:\n" +
            "- The protagonist is the narrator. Refer to them only in first person " +
            "(\"I\", \"me\", \"my\", \"myself\"). Never use their name or \"he/she/they\" for them.\n" +
            "- Every other character is referred to in third person " +
            "(by name or \"he\", \"she\", \"they\") at all times.\n\n" +
            "Weave all elements into a vivid, cohesive narrative. " +
            "Show, don't tell. Let dialogue and action carry the theme and conflict.";

    // ---- Name extraction pattern -----------------------------------------------

    private static final Pattern NAMES_PATTERN = Pattern.compile("NAMES:\\s*(.+)");

    // ---- Constructor ------------------------------------------------------------

    public StoryFlow(final Parser parser) {
        super(parser);
    }

    // ---- Orchestration ---------------------------------------------------------

    @Override
    public Flowable<Event> run(final InvocationContext context) {
        LOG.info("Starting StoryFlow with 6 phases");
        return logPhase(runStep(context, PHASE_1_PROTAGONIST), "Phase 1: Protagonist")
                .concatWith(logPhase(runStepWithFreshHistory(context, PHASE_2_OTHER_CHARACTERS), "Phase 2: Other Characters"))
                .concatWith(logPhase(runProfilingSteps(context), "Phase 3: Character Profiles"))
                .concatWith(logPhase(runStep(context, PHASE_4_THEME), "Phase 4: Theme"))
                .concatWith(logPhase(runStep(context, PHASE_5_SITUATION), "Phase 5: Situation"))
                .concatWith(logPhase(runStep(context, PHASE_6_SCENE), "Phase 6: Scene"));
    }

    /**
     * Wraps a phase's Flowable to log its output events.
     */
    private Flowable<Event> logPhase(final Flowable<Event> phase, final String phaseName) {
        return phase.doOnNext(event -> {
            final String content = event.content()
                    .flatMap(c -> c.parts()
                            .flatMap(parts -> parts.stream()
                                    .filter(p -> p.text().isPresent())
                                    .map(p -> p.text().get())
                                    .findFirst()))
                    .orElse("");
            LOG.info("{} output: {}", phaseName, content);
        });
    }

    // ---- Step execution --------------------------------------------------------

    /**
     * Runs a single-turn step with the normal (cumulative) request processors,
     * appending the given phase prompt to the system instruction.
     */
    private Flowable<Event> runStep(final InvocationContext context, final String phasePrompt) {
        final List<RequestProcessor> processors = new ArrayList<>(requestProcessors);
        processors.add(phaseInstruction(phasePrompt));
        LOG.debug("Executing phase with prompt: {}", phasePrompt);
        return new SingleTurnFlow(processors, responseProcessors).run(context);
    }

    /**
     * Runs a single-turn step where the request's conversation history is stripped down
     * to only the original user message. This prevents prior model outputs from
     * influencing the inference.
     */
    private Flowable<Event> runStepWithFreshHistory(
            final InvocationContext context, final String phasePrompt) {
        final List<RequestProcessor> processors = new ArrayList<>(requestProcessors);
        processors.add(stripModelHistory());
        processors.add(phaseInstruction(phasePrompt));
        LOG.debug("Executing phase with fresh history (user input only) and prompt: {}", phasePrompt);
        return new SingleTurnFlow(processors, responseProcessors).run(context);
    }

    private Flowable<Event> runProfilingSteps(final InvocationContext context) {
        return Flowable.defer(() -> {
            final List<String> names = extractNames(context.session().events());
            LOG.debug("Found {} characters to profile: {}", names.size(), names);

            Flowable<Event> profilingFlow = Flowable.empty();
            for (final String name : names) {
                LOG.debug("Profiling character: {}", name);
                profilingFlow = profilingFlow.concatWith(
                        runStepWithProtagonistOnly(
                                context, String.format(PHASE_3_CHARACTER_PROFILE_TEMPLATE, name)));
            }
            return profilingFlow;
        });
    }

    /**
     * Runs a single-turn step where only the user content and the protagonist profile
     * (the first model turn) are visible. Profiles of previously processed secondary
     * characters are stripped so each character is evaluated independently.
     */
    private Flowable<Event> runStepWithProtagonistOnly(
            final InvocationContext context, final String phasePrompt) {
        final List<RequestProcessor> processors = new ArrayList<>(requestProcessors);
        processors.add(stripNonProtagonistModelHistory());
        processors.add(phaseInstruction(phasePrompt));
        LOG.debug("Executing character profiling phase with protagonist-only history and prompt: {}", phasePrompt);
        return new SingleTurnFlow(processors, responseProcessors).run(context);
    }

    // ---- Request processor factories -------------------------------------------

    /** Appends the phase-specific prompt to the system instruction. */
    private static RequestProcessor phaseInstruction(final String prompt) {
        return (ctx, req) -> {
            final LlmRequest updated = req.toBuilder()
                    .appendInstructions(List.of(prompt))
                    .build();
            return Single.just(RequestProcessor.RequestProcessingResult.create(
                    updated, ImmutableList.of()));
        };
    }

    /**
     * Strips all model-authored turns from request contents, leaving only user messages.
     * Used for Phase 2 so the protagonist profile is not visible when inferring who else
     * is in the scene.
     */
    private static RequestProcessor stripModelHistory() {
        return (ctx, req) -> {
            final List<Content> userOnly = req.contents().stream()
                    .filter(c -> !"model".equals(c.role().orElse("")))
                    .toList();
            final LlmRequest updated = req.toBuilder().contents(userOnly).build();
            return Single.just(RequestProcessor.RequestProcessingResult.create(
                    updated, ImmutableList.of()));
        };
    }

    /**
     * Retains only user-role content and the first model turn (the protagonist profile).
     * Any subsequent model turns — profiles of previously processed secondary characters —
     * are dropped so each character profiling call is evaluated against the same baseline.
     */
    private static RequestProcessor stripNonProtagonistModelHistory() {
        return (ctx, req) -> {
            boolean protagonistSeen = false;
            final List<Content> filtered = new ArrayList<>();
            for (final Content c : req.contents()) {
                final boolean isModel = "model".equals(c.role().orElse(""));
                if (isModel && !protagonistSeen) {
                    protagonistSeen = true;
                    filtered.add(c);
                } else if (!isModel) {
                    filtered.add(c);
                }
                // subsequent model turns are intentionally omitted
            }
            final LlmRequest updated = req.toBuilder().contents(filtered).build();
            return Single.just(RequestProcessor.RequestProcessingResult.create(
                    updated, ImmutableList.of()));
        };
    }

    // ---- Name extraction -------------------------------------------------------

    /**
     * Scans the session events for a model turn containing the NAMES: line
     * that Phase 2 was asked to produce.
     */
    private static List<String> extractNames(final List<Event> events) {
        for (final Event event : events) {
            final String text = event.content()
                    .flatMap(c -> c.parts()
                            .flatMap(parts -> parts.stream()
                                    .filter(p -> p.text().isPresent())
                                    .map(p -> p.text().get())
                                    .findFirst()))
                    .orElse("");

            final Matcher matcher = NAMES_PATTERN.matcher(text);
            if (matcher.find()) {
                return Stream.of(matcher.group(1).split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
        }
        return List.of();
    }
}
