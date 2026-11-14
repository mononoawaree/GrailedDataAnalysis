package org.example.grailedmldemo.fetcher;
import org.example.grailedmldemo.exception.FetchException;

import java.io.IOException;

public interface Fetcher {
    String fetch(String url, int page, Long maxSoldAt) throws FetchException, IOException;
}
