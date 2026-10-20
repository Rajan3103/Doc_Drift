package com.docdrift.Docdrift.dto;

import java.time.LocalDate;

/** Suggestions found on one day, by severity. */
public record DailyDriftCount(LocalDate date, long critical, long high, long medium, long low) {
}
