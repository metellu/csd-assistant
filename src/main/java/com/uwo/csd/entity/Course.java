package com.uwo.csd.entity;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Course {
    private String courseName;
    private List<String> courseCode;
    private String courseLocation;
    private List<String> courseTimes;
}