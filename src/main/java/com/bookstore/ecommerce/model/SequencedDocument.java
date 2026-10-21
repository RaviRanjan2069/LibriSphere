package com.bookstore.ecommerce.model;

/**
 * A MongoDB document with a numeric id taken from a per-collection counter,
 * so URLs like /book/12 and order numbers like #LBS-3 stay short and stable.
 */
public interface SequencedDocument {
    Long getId();

    void setId(Long id);
}
