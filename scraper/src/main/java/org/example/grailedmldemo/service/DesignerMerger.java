package org.example.grailedmldemo.service;

import org.example.grailedmldemo.model.Designer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;

public  class DesignerMerger {

    public record MergeResult(
            Map<String, String> matched,
            Set<Designer> unmatched
    ) {}

    public static MergeResult mergeVestiaireDesigners(
            List<Designer> vestiaireBrands,  // [name, slug]
            List<Designer> grailedBrands     // [name, slug]
    ) {
        // vestiaire_slug -> grailed_slug
        Map<String, String> matched = new LinkedHashMap<>();
        Set<Designer> unmatchedVestiaire = new LinkedHashSet<>();

        // Build Grailed lookup maps
        Map<String, String> grailedBySlug = new HashMap<>();        // slug -> slug

        Map<String, String> manualCollabMap = Map.ofEntries(
                Map.entry("adidas-rick-owens", "adidas"),
                Map.entry("adidas-originals-x-alexander-wang", "adidas"),
                Map.entry("jeremy-scott-pour-adidas", "adidas"),
                Map.entry("stella-mccartney-pour-adidas", "adidas"),
                Map.entry("donald-duck-disney-x-gucci", "gucci"),
                Map.entry("y-3-by-yohji-yamamoto", "y3"),
                Map.entry("wild-and-lethal-trash-by-walter-van-beirendonck", "walter-van-bierondock"),
                Map.entry("vivienne-westwood-red-label", "vivienne-westwood"),
                Map.entry("vivienne-westwood-anglomania", "vivienne-westwood"),
                Map.entry("versace-collection", "versace"),
                Map.entry("sixth-june", "sixth-june-paris"),
                Map.entry("saint-laurent", "saint-laurent-paris"),
                Map.entry("valentino-garavani", "valentino"),
                Map.entry("dg", "dolce-gabbana"),
                Map.entry("maison-martin-margiela", "maison-margiela"),
                Map.entry("boss", "hugo-boss"),
                Map.entry("liujo", "liu-jo"),
                Map.entry("dior-homme", "dior"),
                Map.entry("max-mara-weekend", "weekend-max-mara"),
                Map.entry("bash", "ba-sh"),
                Map.entry("armani-jeans", "emporio-armani"),
                Map.entry("gianni-versace", "versace"),
                Map.entry("jordan", "jordan-brand"),
                Map.entry("red-valentino-garavani", "red-valentino"),
                Map.entry("m-missoni", "missoni"),
                Map.entry("moschino-love", "moschino-love"),
                Map.entry("moschino-cheap-and-chic", "moschino"),
                Map.entry("woolrich", "woolrich-woolen-mills"),
                Map.entry("all-saints", "allsaints"),
                Map.entry("bcbg-max-azria", "bcbgmaxazria"),
                Map.entry("jcrew", "j-crew"),
                Map.entry("calvin-klein-jeans", "calvin-klein"),
                Map.entry("apc", "a-p-c"),
                Map.entry("max-mara-s", "max-mara"),
                Map.entry("max-mara-studio", "max-mara"),
                Map.entry("attico", "the-attico"),
                Map.entry("class-cavalli", "cavali-class"),
                Map.entry("31-phillip-lim", "3-1-phillip-lim"),
                Map.entry("christian-dior", "christian-dior-monsieur"),
                Map.entry("cp-company", "c-p-company"),
                Map.entry("trussardi-jeans", "trussardi"),
                Map.entry("galliano", "john-galliano"),
                Map.entry("pleats-please", "pleats-please-issey-miyake"),
                Map.entry("bikkembergs", "dirk-bikkembergs"),
                Map.entry("a-bathing-ape", "bape"),
                Map.entry("paulshark", "paul-shark"),
                Map.entry("the-frankie-shop", "frankie-shop"),
                Map.entry("cerruti", "cerruti-1881"),
                Map.entry("zegna", "ermenegildo-zegna"),
                Map.entry("love-shack-fancy", "loveshackfancy"),
                Map.entry("g-star-raw", "g-star"),
                Map.entry("ralph-lauren-collection", "ralph-lauren"),
                Map.entry("marciano", "georges-marciano"),
                Map.entry("dunhill", "alfred-dunhill"),
                Map.entry("valentino-by-mario-valentino", "mario-valentino"),
                Map.entry("y-project", "yproject"),
                Map.entry("as98", "a-s-98"),
                Map.entry("lafayette-148-ny", "lafayette-148"),
                Map.entry("victoria-victoria-beckham", "victoria-beckham"),
                Map.entry("moynat-paris", "moynat"),
                Map.entry("play-comme-des-garcons", "comme-des-garcons-play"),
                Map.entry("nike-air-humara-x-jacquemus", "nike"),
                Map.entry("nike-x-undercover", "nike"),
                Map.entry("etudes-studio", "etudes"),
                Map.entry("dc-shoes", "dc"),
                Map.entry("non-signe-unsigned", "other"),
                Map.entry("alc", "a-l-c"),
                Map.entry("atpco", "at-p-co")
        );

        for (Designer ds : grailedBrands) {
            String name = ds.getName();
            String slug = ds.getSlug();
            grailedBySlug.put(slug, slug);
        }
        for (Designer vs : vestiaireBrands) {
            String vName = vs.getName();
            String vSlug = vs.getSlug();

            // Priority 1: Manual map override
            if (manualCollabMap.containsKey(vSlug)) {
                String grailedSlug = manualCollabMap.get(vSlug);
                if (grailedBySlug.containsKey(grailedSlug)) {
                    matched.put(vSlug, grailedSlug);
                    continue;
                }
            }

            // Priority 2: Collab -x- extraction
            if (vSlug.contains("-x-")) {
                String parentSlug = vSlug.substring(0, vSlug.indexOf("-x-"));
                if (grailedBySlug.containsKey(parentSlug)) {
                    matched.put(vSlug, parentSlug);
                    continue;
                }
            }

            // Priority 3: Exact slug match
            if (grailedBySlug.containsKey(vSlug)) {
                matched.put(vSlug, vSlug);
                continue;
            }

            unmatchedVestiaire.add(new Designer(vs.getId(), vName, vSlug, vs.getCount(), vs.getSource()));
        }

        //matched.entrySet().stream().filter(entry -> entry.getKey() != entry.getValue()).forEach(e -> System.out.println(e.getKey() + " -> " + e.getValue()));
        try (BufferedWriter br = new BufferedWriter(new FileWriter("unmatched_stage1.txt"))) {
            for (Designer s : unmatchedVestiaire) {
                br.write(s.getId() + "\t" + s.getName() + "\t" + s.getSlug() + "\t" + s.getCount());
                br.newLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Stage 1 results:");
        System.out.println("  Matched: " + matched.size());
        System.out.println("  Unmatched: " + unmatchedVestiaire.size());
        System.out.println("  Total Vestiaire: " + vestiaireBrands.size());
        System.out.println("  Total Grailed: " + grailedBrands.size());
        unmatchedVestiaire.stream()
                .sorted(Comparator.comparingInt((Designer d) -> Integer.parseInt(d.getCount())).reversed())
                .forEach(e -> System.out.println(e.getId() + "\t" + e.getName() + "\t" + e.getSlug() + "\t" + e.getCount()));
        return new MergeResult(matched, unmatchedVestiaire);
    }


    // Strip everything that isn't a letter or digit
    private static String strip(String slug) {
        return slug.replaceAll("[^a-z0-9]", "");
    }
}
