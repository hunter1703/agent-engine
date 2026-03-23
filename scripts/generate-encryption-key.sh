#!/bin/bash

# WARNING: This script prints the generated encryption key to the terminal and the key
# may appear in your shell history. To avoid saving it to history:
#
#   HISTFILE=/dev/null bash scripts/generate-encryption-key.sh

MONGO_URI=${1:-"mongodb://localhost:27018"}
DATABASE="INFRA"
COLLECTION="InfraConfig"

echo "Generating a new 256-bit AES key for EncryptionService..."
KEY=$(openssl rand -base64 32)

echo ""
echo "=========================================================="
echo "YOUR NEW ENCRYPTION KEY IS:"
echo "$KEY"
echo "=========================================================="
echo "Please save this key in a secure location!"
echo ""

echo "Connecting to MongoDB at $MONGO_URI to update $DATABASE.$COLLECTION..."

mongosh "$MONGO_URI/$DATABASE" --eval "
  const result = db.$COLLECTION.updateOne(
    { type: 'encryption' },
    {
      \$set: {
        type: 'encryption',
        key: '$KEY'
      }
    },
    { upsert: true }
  );
  if (result.upsertedId) {
    print('Created new encryption configuration document.');
  } else if (result.modifiedCount > 0) {
    print('Updated existing encryption configuration document.');
  } else {
    print('Key was already up-to-date.');
  }
"

if [ $? -eq 0 ]; then
  echo ""
  echo "Success! The encryption key has been configured in MongoDB."
else
  echo ""
  echo "Error: Failed to execute mongosh. Ensure MongoDB is running and 'mongosh' is installed."
  echo "You can manually insert the document using the key provided above."
fi
