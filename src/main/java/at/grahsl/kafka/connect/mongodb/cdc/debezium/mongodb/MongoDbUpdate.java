/*
 * Copyright 2008-present MongoDB, Inc.
 * Copyright 2017 Hans-Peter Grahsl.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.grahsl.kafka.connect.mongodb.cdc.debezium.mongodb;

import at.grahsl.kafka.connect.mongodb.cdc.CdcOperation;
import at.grahsl.kafka.connect.mongodb.converter.SinkDocument;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import org.apache.kafka.connect.errors.DataException;
import org.bson.BsonDocument;

import static at.grahsl.kafka.connect.mongodb.cdc.debezium.mongodb.MongoDbHandler.ID_FIELD;
import static at.grahsl.kafka.connect.mongodb.cdc.debezium.mongodb.MongoDbHandler.JSON_ID_FIELD;
import static java.lang.String.format;

public class MongoDbUpdate implements CdcOperation {
    private static final ReplaceOptions REPLACE_OPTIONS = new ReplaceOptions().upsert(true);
    private static final String JSON_DOC_FIELD_PATH = "patch";

    @Override
    public WriteModel<BsonDocument> perform(final SinkDocument doc) {

        BsonDocument valueDoc = doc.getValueDoc().orElseThrow(
                () -> new DataException("error: value doc must not be missing for update operation")
        );

        try {
            BsonDocument updateDoc = BsonDocument.parse(valueDoc.getString(JSON_DOC_FIELD_PATH).getValue());
            //patch contains full new document for replacement
            if (updateDoc.containsKey(ID_FIELD)) {
                BsonDocument filterDoc = new BsonDocument(ID_FIELD, updateDoc.get(ID_FIELD));
                return new ReplaceOneModel<>(filterDoc, updateDoc, REPLACE_OPTIONS);
            }

            //patch contains idempotent change only to update original document with
            BsonDocument keyDoc = doc.getKeyDoc().orElseThrow(
                    () -> new DataException("error: key doc must not be missing for update operation"));

            BsonDocument filterDoc = BsonDocument.parse(format("{%s: %s}", ID_FIELD, keyDoc.getString(JSON_ID_FIELD).getValue()));
            return new UpdateOneModel<>(filterDoc, updateDoc);

        } catch (DataException exc) {
            throw exc;
        } catch (Exception exc) {
            throw new DataException(exc.getMessage(), exc);
        }

    }

}