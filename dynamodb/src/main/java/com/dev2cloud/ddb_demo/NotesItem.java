package com.dev2cloud.ddb_demo;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

// Annotate as a DynamoDB Bean
@DynamoDbBean
public class NotesItem {
    //Set up Data Members that correspond to columns in the Music table
    private String userId;
    private Integer noteId;
    private String note;

    public NotesItem() {
    }

    public NotesItem(String userId, Integer noteId, String note) {
        this.userId = userId;
        this.noteId = noteId;
        this.note = note;
    }

    // Add all accessors and mutators below
    // Declare as DynamoDB Primary key
    // Add all accessors and mutators below
    // Declare as DynamoDB Primary key
    @DynamoDbPartitionKey
    @DynamoDbAttribute("UserId")
    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String UserId) {
        this.userId = UserId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("NoteId")
    public Integer getNoteId() {
        return this.noteId;
    }

    public void setNoteId(Integer NoteId) {
        this.noteId = NoteId;
    }

    // How to override default name mapping
    @DynamoDbAttribute("Notes")
    public String getNotes() {
        return this.note;
    }

    public void setNotes(String Note) {
        this.note = Note;
    }

    @Override
    public String toString() {
        return "Notes [User=" + userId + ", Note Id=" + noteId + ", Notes=" + note + "]";
    }


}