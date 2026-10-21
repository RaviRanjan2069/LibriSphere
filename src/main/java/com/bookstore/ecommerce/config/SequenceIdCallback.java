package com.bookstore.ecommerce.config;

import com.bookstore.ecommerce.model.DatabaseSequence;
import com.bookstore.ecommerce.model.SequencedDocument;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** Assigns the next numeric id to new documents just before they are written. */
@Component
public class SequenceIdCallback implements BeforeConvertCallback<SequencedDocument> {

    private final MongoOperations mongo;

    // @Lazy breaks the cycle: MongoTemplate itself depends on the entity callbacks
    public SequenceIdCallback(@Lazy MongoOperations mongo) {
        this.mongo = mongo;
    }

    @Override
    public SequencedDocument onBeforeConvert(SequencedDocument entity, String collection) {
        if (entity.getId() == null) {
            entity.setId(nextId(collection));
        }
        return entity;
    }

    private long nextId(String collection) {
        DatabaseSequence counter = mongo.findAndModify(
                Query.query(where("_id").is(collection)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                DatabaseSequence.class);
        return counter.getSeq();
    }
}
