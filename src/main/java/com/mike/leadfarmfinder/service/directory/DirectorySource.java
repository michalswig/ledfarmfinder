package com.mike.leadfarmfinder.service.directory;

import java.util.List;

public interface DirectorySource {
    //id źródła w logach
    String sourceName();
    //pobiera URL-e z katalogu
    List<String> fetchFarmUrls();
}
