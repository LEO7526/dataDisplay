package com.example.datadisplay.models;

import java.util.List;

public class PhotoFolder {
    public String name;
    public List<PhotoFolder> folders;  // 👈 add this for subfolders
    public List<String> images;        // existing images
}