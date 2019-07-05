package com.uwo.csd.entity;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class Course implements Serializable {
    private String courseName;
    private List<String> courseCode;
    private List<Map<String,String>> courseTimeLoc;
    private List<String> instructors;
    private String description;
}