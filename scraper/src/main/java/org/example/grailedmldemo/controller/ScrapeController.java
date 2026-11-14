package org.example.grailedmldemo.controller;

import org.example.grailedmldemo.exception.FetchException;
import org.example.grailedmldemo.exception.ParseException;
import org.example.grailedmldemo.service.GrailedScrapeService;

import java.io.IOException;
import java.sql.SQLException;

public class ScrapeController {
    private final GrailedScrapeService grailedScrapeService;

    public ScrapeController(GrailedScrapeService grailedScrapeService) {
        this.grailedScrapeService = grailedScrapeService;
    }

    public void run() throws SQLException, IOException, FetchException, ParseException {
        grailedScrapeService.scrapeAll();
    }
}