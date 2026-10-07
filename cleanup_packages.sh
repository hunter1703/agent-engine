#!/bin/bash

# The GitHub token (GH_TOKEN) and username (ORG) are automatically injected 
# by the Gradle cleanupOldPackages task using your gradle.properties!

ORG="${ORG:-hunter1703}"

# Get all packages for the user
echo "Fetching packages for user $ORG..."
PACKAGES=$(gh api -H "Accept: application/vnd.github+json" /users/$ORG/packages?package_type=maven --jq '.[].name')

for PACKAGE in $PACKAGES; do
  echo "Processing package: $PACKAGE"
  
  # Get all versions for this package
  VERSIONS=$(gh api -H "Accept: application/vnd.github+json" /users/$ORG/packages/maven/$PACKAGE/versions --jq '.[] | select(.name | contains("SNAPSHOT")) | "\(.id) \(.created_at)"')
  
  # Sort versions by creation date (newest first) and keep only the first 2 (or skip deletion if fewer than 2)
  # Then extract the IDs of the older versions to delete
  VERSION_IDS_TO_DELETE=$(echo "$VERSIONS" | sort -k2 -r | tail -n +3 | awk '{print $1}')
  
  if [ -z "$VERSION_IDS_TO_DELETE" ]; then
    echo "  No old snapshots to delete."
  else
    for VERSION_ID in $VERSION_IDS_TO_DELETE; do
      echo "  Deleting version ID $VERSION_ID from $PACKAGE..."
      gh api --method DELETE -H "Accept: application/vnd.github+json" /users/$ORG/packages/maven/$PACKAGE/versions/$VERSION_ID
    done
  fi
done

echo "Cleanup complete!"
