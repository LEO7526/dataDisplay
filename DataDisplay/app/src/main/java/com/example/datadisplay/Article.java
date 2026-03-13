package com.example.datadisplay;

public class Article {
    public String title;
    public String tag;
    public String content;
    public String activityName;

    public String filename;
    public String type;
    public String category;
    public String folder;
    public String itemName;

    public String bookAuthor;
    public String bookContent;
    public String bookTag;

    public Article(String title, String filename, String type,
                   String category, String folder, String itemName) {
        this.title = title;
        this.filename = filename;
        this.type = type;
        this.category = category;
        this.folder = folder;
        this.itemName = itemName;

        this.tag = type;
        this.content = "";
        this.activityName = "";

        this.bookAuthor = "";
        this.bookContent = "";
        this.bookTag = "General";
    }
}
