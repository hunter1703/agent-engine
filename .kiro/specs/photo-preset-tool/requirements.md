# Requirements Document

## Introduction

The photo-preset-tool is a LangChain4j-compatible agent tool that enables an AI agent to apply
rich, Lightroom-style photo editing presets to images programmatically. The agent's LLM generates
a structured preset specification (JSON), and the tool executes the full editing pipeline using
FFmpeg. The tool integrates into the agent-engine plugin system as a `@DiscoverableTool` and
supports parametric adjustments including exposure, contrast, saturation, white balance, tone
curves, color grading, sharpening, and LUT application.

---

## Glossary

- **Photo_Preset_Tool**: The agent tool registered in the agent-engine plugin system that accepts
  a preset specification and an input image path, applies the editing pipeline, and returns the
  output image path.
- **Preset**: A bounded, validated JSON document describing a complete set of photo editing
  parameters to apply to an image.
- **Preset_Schema**: The strict, versioned JSON schema that defines all valid fields, types, and
  value ranges for a Preset.
- **Preset_Validator**: The component responsible for validating a Preset against the
  Preset_Schema before pipeline execution.
- **Pipeline_Executor**: The component that translates a validated Preset into an ordered sequence
  of FFmpeg filter operations and executes them via `ProcessBuilder`.
- **LUT_Generator**: The component that converts parametric Preset definitions into `.cube` LUT
  files for use in the FFmpeg pipeline.
- **FFmpeg**: The external command-line tool used to apply image processing operations. Invoked
  via Java `ProcessBuilder`.
- **Tone_Curve**: A per-channel (R, G, B, or composite) mapping of input pixel values to output
  pixel values, expressed as a list of control points.
- **White_Balance**: A color temperature and tint adjustment approximated via RGB channel scaling.
- **Color_Grading**: Shadow and highlight colorization adjustments applied to the image.
- **LUT**: A 3D Look-Up Table in `.cube` format that maps input RGB values to output RGB values.
- **Agent**: The LangChain4j-based AI agent that calls the Photo_Preset_Tool as part of its
  reasoning loop.

---

## Requirements

### Requirement 1: Tool Registration and Plugin Integration

**User Story:** As an agent developer, I want the photo-preset-tool to be discoverable and
loadable by the agent-engine plugin system, so that agents can invoke it without manual wiring.

#### Acceptance Criteria

1. THE Photo_Preset_Tool SHALL be annotated with `@DiscoverableTool` so that the
   `DiscoveredToolProviders` CDI bean registers it automatically at startup.
2. THE Photo_Preset_Tool SHALL extend the `Tool` base class and expose a single `execute` method
   as its callable entry point.
3. THE Photo_Preset_Tool SHALL declare a `ToolDescriptor` with a unique tool name, a
   human-readable description, and a `ToolRiskLevel` of `MEDIUM`.
4. WHEN the agent-engine starts, THE Photo_Preset_Tool SHALL appear in the list of available
   tools returned by `ToolService`.

---

### Requirement 2: Preset Schema Definition

**User Story:** As an AI agent, I want a strict, bounded preset schema, so that I can generate
valid preset specifications without producing unsafe or unpredictable image processing operations.

#### Acceptance Criteria

1. THE Preset_Schema SHALL define the following top-level fields, all optional unless stated:
   - `inputPath` (String, required): absolute or relative path to the input image file.
   - `outputPath` (String, optional): absolute or relative path for the output image file; if
     absent, derived from `inputPath` by appending `_preset` before the file extension.
   - `exposure` (Float, range −5.0 to +5.0, in EV stops)
   - `contrast` (Float, range −100.0 to +100.0)
   - `saturation` (Float, range −100.0 to +100.0)
   - `sharpening` (Float, range 0.0 to 100.0)
   - `whiteBalance` (object with `temperature` Integer −100 to +100 and `tint` Integer −100 to +100)
   - `toneCurve` (object with optional per-channel arrays `rgb`, `red`, `green`, `blue`, each a
     list of `[input, output]` control point pairs where input and output are Floats in 0.0–1.0)
   - `colorGrading` (object with optional `shadows` and `highlights` sub-objects, each containing
     `hue` Float 0.0–360.0, `saturation` Float 0.0–100.0, `luminance` Float −100.0 to +100.0)
   - `lut` (object with `path` String pointing to a `.cube` file, and `intensity` Float 0.0–1.0)
2. THE Preset_Schema SHALL be represented as a Java record (`PhotoPreset`) with nested records for
   `WhiteBalance`, `ToneCurve`, `ColorGrading`, `GradingLayer`, and `LutConfig`.
3. WHEN a Preset field value falls outside its defined range, THE Preset_Validator SHALL clamp
   the value to the nearest boundary and log a warning.
4. WHEN a Preset contains an unrecognised field, THE Preset_Validator SHALL ignore the field and
   continue processing.

---

### Requirement 3: Preset Validation

**User Story:** As an agent developer, I want all presets to be validated before execution, so
that malformed AI-generated presets do not cause FFmpeg errors or corrupt output files.

#### Acceptance Criteria

1. WHEN the `execute` method is called, THE Preset_Validator SHALL validate the `PhotoPreset`
   input before any FFmpeg process is started.
2. IF a required field (`inputPath`) is null or blank, THEN THE Photo_Preset_Tool SHALL return an
   error map with key `"error"` and a descriptive message without starting any process.
3. IF the `inputPath` does not resolve to a readable file, THEN THE Photo_Preset_Tool SHALL
   return an error map with key `"error"` and message `"Input file not found: <path>"`.
4. IF the `lut.path` field is present and does not resolve to a readable `.cube` file, THEN THE
   Photo_Preset_Tool SHALL return an error map with key `"error"` and message
   `"LUT file not found: <path>"`.
5. WHEN validation passes, THE Preset_Validator SHALL return the clamped, normalised
   `PhotoPreset` to the Pipeline_Executor.

---

### Requirement 4: FFmpeg Pipeline Execution

**User Story:** As an AI agent, I want the preset to be applied to an image via FFmpeg, so that
the output image reflects all specified adjustments in a single processing pass.

#### Acceptance Criteria

1. WHEN a validated Preset is received, THE Pipeline_Executor SHALL construct a single FFmpeg
   `filtergraph` string that encodes all active adjustments in the following order:
   exposure → white balance → contrast → saturation → tone curves → color grading → sharpening → LUT.
2. THE Pipeline_Executor SHALL invoke FFmpeg via `ProcessBuilder` with the constructed
   filtergraph, the resolved `inputPath`, and the resolved `outputPath`.
3. WHEN `outputPath` is not specified in the tool call, THE Pipeline_Executor SHALL derive the
   output path by appending `_preset` before the file extension of the input path.
4. WHEN FFmpeg exits with a non-zero exit code, THE Photo_Preset_Tool SHALL return an error map
   containing `"error"` and the first 2,000 characters of FFmpeg's stderr output.
5. WHEN FFmpeg exits with exit code 0, THE Photo_Preset_Tool SHALL return a result map containing
   `"outputPath"` (the resolved output file path) and `"appliedPreset"` (the serialised, clamped
   preset as a JSON string).
6. THE Pipeline_Executor SHALL enforce a configurable execution timeout defaulting to 120 seconds;
   WHEN the timeout is exceeded, THE Photo_Preset_Tool SHALL terminate the FFmpeg process and
   return an error map with key `"error"` and value `"FFmpeg execution timed out"`.
7. WHILE the FFmpeg process is running, THE Pipeline_Executor SHALL consume both stdout and stderr
   on separate threads to prevent process blocking due to full output buffers.
8. THE Photo_Preset_Tool SHALL support concurrent invocations; each call SHALL execute in an
   independent `ProcessBuilder` process with no shared mutable state between concurrent calls.

---

### Requirement 5: Exposure Adjustment

**User Story:** As an AI agent, I want to adjust image exposure in EV stops, so that I can
brighten or darken images as part of a preset.

#### Acceptance Criteria

1. WHEN `exposure` is present in the Preset, THE Pipeline_Executor SHALL translate the EV value
   to a multiplicative gain factor using the formula `gain = 2^exposure` and apply it via the
   FFmpeg `exposure` filter.
2. WHEN `exposure` is 0.0 or absent, THE Pipeline_Executor SHALL omit the exposure filter from
   the filtergraph.

---

### Requirement 6: White Balance Adjustment

**User Story:** As an AI agent, I want to adjust white balance via temperature and tint, so that
I can correct or stylise the colour cast of an image.

#### Acceptance Criteria

1. WHEN `whiteBalance` is present in the Preset, THE Pipeline_Executor SHALL translate
   `temperature` and `tint` values to per-channel RGB multipliers using a linear approximation:
   - `temperature` shifts the red/blue ratio: positive values increase red and decrease blue;
     negative values increase blue and decrease red.
   - `tint` shifts the green channel: positive values increase green; negative values decrease green.
2. THE Pipeline_Executor SHALL apply the computed RGB multipliers via the FFmpeg `colorchannelmixer`
   filter.
3. WHEN both `temperature` and `tint` are 0 or `whiteBalance` is absent, THE Pipeline_Executor
   SHALL omit the white balance filter from the filtergraph.

---

### Requirement 7: Contrast and Saturation Adjustment

**User Story:** As an AI agent, I want to adjust contrast and saturation, so that I can control
the tonal range and colour intensity of an image.

#### Acceptance Criteria

1. WHEN `contrast` is non-zero, THE Pipeline_Executor SHALL apply contrast adjustment via the
   FFmpeg `colorlevels` filter by mapping the contrast value to input level adjustments that
   compress or expand the tonal range symmetrically around the midpoint.
2. WHEN `saturation` is non-zero, THE Pipeline_Executor SHALL apply saturation adjustment via the
   FFmpeg `hue` filter's `s` parameter, mapping the −100 to +100 range to a 0.0–2.0 multiplier
   (0.0 = fully desaturated, 1.0 = unchanged, 2.0 = double saturation).
3. WHEN `contrast` is 0.0 or absent, THE Pipeline_Executor SHALL omit the contrast filter.
4. WHEN `saturation` is 0.0 or absent, THE Pipeline_Executor SHALL omit the saturation filter.

---

### Requirement 8: Tone Curve Application

**User Story:** As an AI agent, I want to apply per-channel tone curves, so that I can achieve
precise tonal control over shadows, midtones, and highlights.

#### Acceptance Criteria

1. WHEN `toneCurve` is present and contains at least one channel with two or more control points,
   THE Pipeline_Executor SHALL apply the curves via the FFmpeg `curves` filter.
2. THE Pipeline_Executor SHALL map the `rgb` channel to the FFmpeg `master` curve parameter, and
   `red`, `green`, `blue` channels to their respective FFmpeg curve parameters.
3. WHEN a channel has fewer than two control points, THE Pipeline_Executor SHALL omit that channel
   from the `curves` filter.
4. THE Pipeline_Executor SHALL format each control point as `"input/output"` pairs separated by
   spaces, as required by the FFmpeg `curves` filter syntax.

---

### Requirement 9: Color Grading

**User Story:** As an AI agent, I want to apply shadow and highlight color grading, so that I can
add stylistic colour tones to the dark and bright regions of an image.

#### Acceptance Criteria

1. WHEN `colorGrading.shadows` or `colorGrading.highlights` is present, THE Pipeline_Executor
   SHALL apply color grading via the FFmpeg `colorbalance` filter.
2. THE Pipeline_Executor SHALL translate `hue` and `saturation` from the grading layer into
   red/green/blue channel offsets for the `colorbalance` filter's shadow or highlight parameters.
3. WHEN both `shadows` and `highlights` are absent or `colorGrading` is absent, THE
   Pipeline_Executor SHALL omit the color grading filter from the filtergraph.

---

### Requirement 10: Sharpening

**User Story:** As an AI agent, I want to apply sharpening to an image, so that I can enhance
edge detail as part of a preset.

#### Acceptance Criteria

1. WHEN `sharpening` is greater than 0.0, THE Pipeline_Executor SHALL apply sharpening via the
   FFmpeg `unsharp` filter with a strength proportional to the `sharpening` value mapped to the
   range 0.0–5.0 for the luma sharpening amount parameter.
2. WHEN `sharpening` is 0.0 or absent, THE Pipeline_Executor SHALL omit the sharpening filter
   from the filtergraph.

---

### Requirement 11: LUT Application

**User Story:** As an AI agent, I want to apply a `.cube` LUT file to an image, so that I can
use pre-built colour grades as part of a preset.

#### Acceptance Criteria

1. WHEN `lut` is present and `lut.path` resolves to a readable `.cube` file, THE Pipeline_Executor
   SHALL apply the LUT via the FFmpeg `lut3d` filter.
2. THE Pipeline_Executor SHALL apply the LUT as the final step in the filtergraph, after all
   parametric adjustments.
3. WHEN `lut.intensity` is less than 1.0, THE Pipeline_Executor SHALL blend the LUT output with
   the pre-LUT image using the FFmpeg `blend` filter to achieve the specified intensity.
4. WHEN `lut` is absent, THE Pipeline_Executor SHALL omit the LUT filter from the filtergraph.

---

### Requirement 12: Programmatic LUT Generation

**User Story:** As an AI agent, I want to generate a `.cube` LUT file from a parametric preset,
so that I can persist and reuse colour grades without re-specifying all parameters.

#### Acceptance Criteria

1. THE LUT_Generator SHALL be a utility class exposing a static method
   `generate(PhotoPreset preset, Path outputPath)` that writes a valid `.cube` file to `outputPath`.
2. THE LUT_Generator SHALL produce a 33×33×33 identity LUT and apply the parametric adjustments
   (exposure, white balance, contrast, saturation, tone curves, color grading) to each lattice
   point.
3. THE LUT_Generator SHALL write the `.cube` file with the standard header:
   `LUT_3D_SIZE 33`, followed by `DOMAIN_MIN 0.0 0.0 0.0`, `DOMAIN_MAX 1.0 1.0 1.0`, and then
   one `R G B` triplet per line in B-fastest order.
4. WHEN `outputPath` already exists, THE LUT_Generator SHALL overwrite the file.
5. FOR ALL valid `PhotoPreset` inputs `p`, generating a LUT from `p` and applying it to an image
   SHALL produce a result visually equivalent (within floating-point rounding tolerance of ±1/255
   per channel) to applying the parametric pipeline directly (round-trip equivalence property).

---

### Requirement 13: Output and Result Contract

**User Story:** As an AI agent, I want a consistent, structured result from the tool, so that I
can reliably parse the output and continue my reasoning.

#### Acceptance Criteria

1. WHEN the tool executes successfully, THE Photo_Preset_Tool SHALL return a `Map<String, Object>`
   containing:
   - `"outputPath"`: the absolute path of the written output image as a String.
   - `"appliedPreset"`: the clamped preset serialised as a compact JSON string.
   - `"durationMs"`: the wall-clock execution time in milliseconds as a Long.
2. WHEN the tool fails for any reason, THE Photo_Preset_Tool SHALL return a `Map<String, Object>`
   containing `"error"` as a String with a human-readable description of the failure.
3. THE Photo_Preset_Tool SHALL never throw an unchecked exception to the caller; all errors SHALL
   be captured and returned in the result map.

---

### Requirement 14: Supported Image Formats

**User Story:** As an AI agent, I want the tool to support common image formats, so that I can
apply presets to images produced by cameras and other tools.

#### Acceptance Criteria

1. THE Photo_Preset_Tool SHALL accept input images in JPEG, PNG, TIFF, and WebP formats.
2. THE Photo_Preset_Tool SHALL write output images in the same format as the input image, inferred
   from the input file extension.
3. IF the input file extension is not one of `jpg`, `jpeg`, `png`, `tiff`, `tif`, or `webp`
   (case-insensitive), THEN THE Photo_Preset_Tool SHALL return an error map with key `"error"`
   and message `"Unsupported image format: <extension>"`.
