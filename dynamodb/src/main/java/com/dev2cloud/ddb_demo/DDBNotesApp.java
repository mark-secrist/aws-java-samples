package com.dev2cloud.ddb_demo;

import java.util.List;

/**
 * Demonstrates the use of the DynamoDB Stabdard client API
 *
 */
public class DDBNotesApp {
    public static void main(String[] args) {
        NotesItem item = new NotesItem("student1", 1, "I love DDB-mod");
        NotesItem item2 = new NotesItem("student1", 2, "I still love DDB-mod");

        NotesRepository notesItemRepository = new NotesRepository();
        // Put using the standard DDB client
        notesItemRepository.putItem(item.getUserId(), item.getNoteId(), item.getNotes());
        notesItemRepository.putItem(item2.getUserId(), item2.getNoteId(), item2.getNotes());

        NotesItem itemFetched = notesItemRepository.getItem(item.getUserId(), item.getNoteId());
        System.out.println(itemFetched);
        NotesItem itemFetched2 = notesItemRepository.getItem(item2.getUserId(), item2.getNoteId());
        System.out.println(itemFetched2);

        List<NotesItem> results = notesItemRepository.getNotesByUserid(item.getUserId());
        System.out.println(results);

        System.out.println("Results from PartiQL query");
        List<NotesItem> results2 = notesItemRepository.getNotesByUseridPartiQL(item.getUserId());
        System.out.println(results2);
    }
}
