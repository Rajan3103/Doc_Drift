package com.docdrift.Docdrift.dto;

/** Counts of suggestions still awaiting review, across all reports. */
public record ReviewSummary(long pending, long critical, long breaking) {
}
