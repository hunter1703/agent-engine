#!/bin/bash

# Default MongoDB URI to the one used by Agent Engine
MONGO_URI=${1:-"mongodb://localhost:27018"}
DATABASE="INFRA"
COLLECTION="InfraConfig"

echo "Generating a new 256-bit AES key for EncryptionService..."
# Generate 32 random bytes and base64 encode them
KEY=$(openssl rand -base64 32)

echo ""
echo "=========================================================="
echo "YOUR NEW ENCRYPTION KEY IS:"
echo "$KEY"
echo "=========================================================="
echo "Please save this key in a secure location!"
echo ""

echo "Connecting to MongoDB at $MONGO_URI to update $DATABASE.$COLLECTION..."

# Use mongosh to upsert the encryption config document
# The POJO Codec uses BsonDiscriminator(key="type", value="encryption")
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
    print('✅ Created new encryption configuration document.');
  } else if (result.modifiedCount > 0) {
    print('✅ Updated existing encryption configuration document.');
  } else {
    print('✅ Key was already up-to-date.');
  }
"

if [ $? -eq 0 ]; then
  echo ""
  echo "Success! The encryption key has been configured in MongoDB."
else
  echo ""
  echo "❌ Error: Failed to execute mongosh. Ensure MongoDB is running and 'mongosh' is installed."
  echo "You can manually insert the document using the key provided above."
fi
