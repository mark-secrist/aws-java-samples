package com.dev2cloud.ddb_demo;

import java.util.List;

/**
 * Demonstrates the use of the DynamoDB Enhanced Client API documented
 * here: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/dynamodb-enhanced-client.html
 *
 */
public class DDBEnhancedNotesApp {
    public static void main( String[] args )
    {
        NotesItem item = new NotesItem("student1", 1, "I love DDB");
        NotesItem item2 = new NotesItem("student1", 2, "I still love DDB");

        NotesEnhancedRepository notesItemRepository = new NotesEnhancedRepository();
        // Put using the enhanced client
        notesItemRepository.putItem(item);
        // Put using the standard DDB client
        notesItemRepository.putItem(item2);
        NotesItem itemFetched = notesItemRepository.getItem(item.getUserId(),item.getNoteId());
        System.out.println(itemFetched);
        NotesItem itemFetched2 = notesItemRepository.getItem(item2.getUserId(),item2.getNoteId());
        System.out.println(itemFetched2);
        //List<NotesItem> results = notesItemRepository.getNotesEnhanced(item.getUserId());
        List<NotesItem> results = notesItemRepository.findNotesForUser(item.getUserId());
        System.out.println(results);
    }
}
