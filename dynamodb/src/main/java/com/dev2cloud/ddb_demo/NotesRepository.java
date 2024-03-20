package com.dev2cloud.ddb_demo;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotesRepository {
    private static final String TABLE_NAME = "Notes";
    private final DynamoDbClient dynamoDbClient;

    public NotesRepository() {
        // Simplistic client creation
        dynamoDbClient = DynamoDbClient.create();
        /* Alternative approach using a profile
        dynamoDbClient = DynamoDbClient.builder()
                .region(Region.US_WEST_2)
                .credentialsProvider(
                        ProfileCredentialsProvider.builder()
                                .profileName("app-user")
                                .build()
                )
                .build();
         */
    }

    /**
     * Put an item into the DynamoDb table using the conventional DynamoDbClient
     *
     * @param userId
     * @param noteId
     * @param note
     */
    public void putItem(String userId, Integer noteId, String note) {
        HashMap<String,AttributeValue> itemValues = new HashMap<>();
        itemValues.put("UserId", AttributeValue.builder().s(userId).build());
        itemValues.put("NoteId", AttributeValue.builder().n(noteId.toString()).build());
        itemValues.put("Notes", AttributeValue.builder().s(note).build());
        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(itemValues)
                .build();
        try {
            dynamoDbClient.putItem(request);
        } catch (DynamoDbException ex) {
            System.err.println(ex.getMessage());
        }
    }

    /**
     * Get an item given the userId and NoteId values
     */
    public NotesItem getItem(String userId, Integer noteId) {
        HashMap<String, AttributeValue> key = new HashMap<>();
        key.put("UserId", AttributeValue.builder().s(userId).build());
        key.put("NoteId", AttributeValue.builder().n(noteId.toString()).build());

        GetItemRequest request = GetItemRequest.builder()
                .key(key)
                .tableName(TABLE_NAME)
                .build();
        try {
            NotesItem item = new NotesItem();
            Map<String, AttributeValue> result = dynamoDbClient.getItem(request).item();
            item.setUserId(result.get("UserId").s());
            item.setNoteId(Integer.parseInt(result.get("NoteId").n()));
            item.setNotes(result.get("Notes").s());
            return item;

        } catch (DynamoDbException ex) {
            System.err.println(ex.getMessage());
            return null;
        }

    }

    /**
     * Simple query using just the partition key to find all the notes for a user

     *
     * @param userId
     * @return
     */
    public List<NotesItem> getNotes(String userId) {
        List<NotesItem> resultList = new ArrayList<>();

        HashMap<String, AttributeValue> attrValues = new HashMap<>();
        attrValues.put(":v_UserId", AttributeValue.builder().s(userId).build());

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("UserId = :v_UserId")
                .expressionAttributeValues(attrValues)
                .build();

        try {
            QueryResponse response = dynamoDbClient.query(queryRequest);
            response.items().forEach(item -> {
                NotesItem notesItem = new NotesItem();
                notesItem.setUserId(item.get("UserId").s());
                notesItem.setNoteId(Integer.parseInt(item.get("NoteId").n()));
                notesItem.setNotes(item.get("Notes").s());
                resultList.add(notesItem);
            });
        } catch (DynamoDbException ex) {
            System.err.println(ex.getMessage());
        }

        return resultList;
    }
}
