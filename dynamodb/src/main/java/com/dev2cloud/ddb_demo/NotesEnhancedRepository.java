package com.dev2cloud.ddb_demo;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.stream.Collectors;

public class NotesEnhancedRepository {
    private static final String TABLE_NAME = "Notes";

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<NotesItem> notesTable ;

    public NotesEnhancedRepository() {
        enhancedClient = DynamoDbEnhancedClient.create();
        notesTable = enhancedClient.table("Notes", TableSchema.fromBean(NotesItem.class));
    }

    /**
     * Put a NotesItem into the DynamoDb table using the enhanced client
     */
    public void putItem(NotesItem notesItem) {
        notesTable.putItem(notesItem);
    }

    /**
     * Update a NotesItem in the DynamoDb table
     */
    public void updateItem(NotesItem notesItem) {
        notesTable.updateItem(notesItem);

    }

    /**
     * Get a notesItem from the DynamoDb table using the enhanced client.
     */
    public NotesItem getItem(String userId, Integer noteId) {
        Key key = Key.builder().partitionValue(userId).sortValue(noteId).build();
        return notesTable.getItem(key);
    }

    /**
     * Find a list of notes for userId using the enhanced client.
     *
     */
    public List<NotesItem> findNotesForUser(String userId) {
        //List<NotesItem> resultList = new ArrayList<>();
        QueryConditional keyEqual = QueryConditional.keyEqualTo(b -> b.partitionValue(userId));
        QueryEnhancedRequest tableQuery = QueryEnhancedRequest.builder()
                .queryConditional(keyEqual).build();

        PageIterable<NotesItem> results =notesTable.query(tableQuery);
        return results.items().stream().collect(Collectors.toList());
    }
}
