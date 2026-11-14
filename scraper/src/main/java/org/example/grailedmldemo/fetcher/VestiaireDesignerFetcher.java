package org.example.grailedmldemo.fetcher;

import com.google.gson.*;

import java.io.*;

import org.example.grailedmldemo.config.ProxyConfig;
import org.example.grailedmldemo.model.VestiaireDesignerRaw;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class VestiaireDesignerFetcher {

    private static final String BRANDS_URL = "https://us.vestiairecollective.com/brands/";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    public List<VestiaireDesignerRaw> fetchBrandList() throws Exception {
        List<VestiaireDesignerRaw> brands = new ArrayList<>();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BRANDS_URL))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        Document doc = Jsoup.parse(response.body());
        Elements links = doc.select("a[data-link-type='native'][rel='internal-marques']");

        for (Element link : links) {
            String href = link.attr("href");
            String name = link.text();
            if (href == null || href.isEmpty() || name == null || name.isEmpty()) continue;

            VestiaireDesignerRaw designer = new VestiaireDesignerRaw();
            designer.setName(name);
            designer.setSlug(href.replaceAll("^/|/$", ""));
            brands.add(designer);
        }

        System.out.println("Fetched " + brands.size() + " brands from catalog");
        return brands;
    }

    public void enrichBrands(List<VestiaireDesignerRaw> brands) throws Exception {
        // Load already collected slugs to skip on resume
        Set<String> alreadyCollected = new HashSet<>();
        File file = new File("vestiaire_brands.txt");
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.readLine(); // skip header
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\t");
                    if (parts.length >= 3) {
                        alreadyCollected.add(parts[2]); // slug is third column
                    }
                }
            }
            System.out.println("Resuming - skipping " + alreadyCollected.size() + " already collected brands");
        }

        HttpClient proxiedClient = HttpClient.newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress("us.decodo.com", 10000)))
                .authenticator(new java.net.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                ProxyConfig.getProxyUsername(),
                                ProxyConfig.getProxyPassword().toCharArray()
                        );
                    }
                })
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        boolean writeHeader = !file.exists() || file.length() == 0;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("vestiaire_brands.txt", true))) {
            if (writeHeader) {
                writer.write("id\tname\tslug\tcount");
                writer.newLine();
            }

            int current = 0;
            int total = brands.size();
            int collected = 0;
            int failed = 0;

            for (VestiaireDesignerRaw designer : brands) {
                current++;
                String slug = designer.getSlug();

                if (alreadyCollected.contains(slug)) continue;

                String body = "{\"pagination\":{\"offset\":0,\"limit\":0},"
                        + "\"fields\":[],"
                        + "\"facets\":{\"fields\":[\"brand\"],\"stats\":[]},"
                        + "\"q\":null,"
                        + "\"sortBy\":\"relevance\","
                        + "\"filters\":{\"catalogLinksWithoutLanguage\":[\"/" + slug + "/\"]},"
                        + "\"locale\":{\"country\":\"US\",\"currency\":\"USD\",\"language\":\"us\",\"sizeType\":\"US\"},"
                        + "\"mySizes\":null}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://search.vestiairecollective.com/v1/product/search"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("Origin", "https://us.vestiairecollective.com")
                        .header("Referer", "https://us.vestiairecollective.com/")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                try {
                    HttpResponse<String> response = proxiedClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 403) {
                        failed++;
                        System.out.println(current + "/" + total + " Rate limited for " + slug + " - sleeping 60s");
                        Thread.sleep(60000);
                        continue;
                    }

                    if (response.statusCode() != 200) {
                        failed++;
                        System.out.println(current + "/" + total + " Failed: " + response.statusCode() + " for " + slug);
                        continue;
                    }

                    String json = response.body();
                    JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
                    JsonArray brandArray = jsonObject.getAsJsonObject("facets")
                            .getAsJsonObject("fields")
                            .getAsJsonArray("brand");

                    if (brandArray != null && !brandArray.isEmpty()) {
                        designer.setId(brandArray.get(0).getAsJsonObject().get("id").getAsString());
                        designer.setCount(brandArray.get(0).getAsJsonObject().get("count").getAsString());
                    }

                    writer.write(designer.getId() + "\t" + designer.getName() + "\t" + designer.getSlug() + "\t" + designer.getCount());
                    writer.newLine();
                    writer.flush();
                    collected++;

                    System.out.println(current + "/" + total + " - " + designer.getName() + " id=" + designer.getId() + " count=" + designer.getCount());

                } catch (Exception e) {
                    failed++;
                    System.out.println(current + "/" + total + " Error: " + slug + " - " + e.getMessage());
                }

                int delay = 1500 + (int) (Math.random() * 2000);
                Thread.sleep(delay);
            }

            System.out.println("Done. Collected: " + collected + " Failed: " + failed);
        }
    }

    public void enrichBrand(String slug) throws Exception {
        // Load already collected slugs to skip on resume
        VestiaireDesignerRaw designer = new VestiaireDesignerRaw();
        Set<String> alreadyCollected = new HashSet<>();
        File file = new File("vestiaire_brands.txt");
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.readLine(); // skip header
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\t");
                    if (parts.length >= 3) {
                        alreadyCollected.add(parts[2]); // slug is third column
                    }
                }
            }
            System.out.println("Resuming - skipping " + alreadyCollected.size() + " already collected brands");
        }

        HttpClient proxiedClient = HttpClient.newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress("us.decodo.com", 10000)))
                .authenticator(new java.net.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                ProxyConfig.getProxyUsername(),
                                ProxyConfig.getProxyPassword().toCharArray()
                        );
                    }
                })
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        boolean writeHeader = !file.exists() || file.length() == 0;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("vestiaire_brands.txt", true))) {
            if (writeHeader) {
                writer.write("id\tname\tslug\tcount");
                writer.newLine();
            }
            String body = "{\"pagination\":{\"offset\":0,\"limit\":0},"
                    + "\"fields\":[],"
                    + "\"facets\":{\"fields\":[\"brand\"],\"stats\":[]},"
                    + "\"q\":null,"
                    + "\"sortBy\":\"relevance\","
                    + "\"filters\":{\"catalogLinksWithoutLanguage\":[\"/" + slug + "/\"]},"
                    + "\"locale\":{\"country\":\"US\",\"currency\":\"USD\",\"language\":\"us\",\"sizeType\":\"US\"},"
                    + "\"mySizes\":null}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://search.vestiairecollective.com/v1/product/search"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Origin", "https://us.vestiairecollective.com")
                    .header("Referer", "https://us.vestiairecollective.com/")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            try {
                HttpResponse<String> response = proxiedClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 403) {
                    System.out.println("Rate limited for " + slug + " - sleeping 60s");
                    Thread.sleep(60000);
                }

                if (response.statusCode() != 200) {
                    System.out.println(" Failed: " + response.statusCode() + " for " + slug);
                }

                String json = response.body();
                JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
                JsonArray brandArray = jsonObject.getAsJsonObject("facets")
                        .getAsJsonObject("fields")
                        .getAsJsonArray("brand");

                if (brandArray != null && !brandArray.isEmpty()) {
                    designer.setId(brandArray.get(0).getAsJsonObject().get("id").getAsString());
                    designer.setCount(brandArray.get(0).getAsJsonObject().get("count").getAsString());
                }

                writer.write(designer.getId() + "\t" + designer.getName() + "\t" + designer.getSlug() + "\t" + designer.getCount());
                writer.newLine();
                writer.flush();

                System.out.println(designer.getName() + " id=" + designer.getId() + " count=" + designer.getCount());

            } catch (Exception e) {
                System.out.println("Error: " + slug + " - " + e.getMessage());
            }

            int delay = 1500 + (int) (Math.random() * 2000);
            Thread.sleep(delay);
        }
    }
}