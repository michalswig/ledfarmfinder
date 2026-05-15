package com.mike.leadfarmfinder.service.serpquery;

public class SerpQueryImproverPromptBuilder {

    String build(String weakQuery, int score) {
        return """
                Jesteś ekspertem od SERP query generation dla systemu który szuka
                indywidualnych stron www niemieckich gospodarstw rolnych sprzedających
                bezpośrednio (Direktverkauf).
                
                SŁABE QUERY DO ULEPSZENIA:
                "%s"
                AKTUALNY SCORE: %d/100 (próg słabości: <40)
                
                ZASADY GENEROWANIA:
                1. Każde query MUSI zawierać co najmniej jeden structural filter który eliminuje katalogi:
                   - fragment Impressum: "Verantwortlich für den Inhalt", "Inhaber", "GbR", "Steuernummer"
                   - first-person: "auf unserem Hof", "wir bauen an", "aus eigenem Anbau"
                   - operational: "Abholtermin", "Vorbestellung", "Liefertag"
                2. Dodaj konkretny region: Landkreis lub krajobraz (nie samo Bundesland).
                3. Dodaj niszowy produkt rolny (Topinambur, Pastinaken, Spargel, Erdbeeren, Kartoffeln).
                4. Zawsze dodaj intent: Kontakt, Adresse lub Telefon.
                5. Długość: 4-7 słów. Powyżej 8 słów skuteczność SERP spada.
                6. Nie duplikuj regionu na dwóch poziomach (np. Bayern + München).
                
                Wygeneruj dokładnie 3 nowe queries które są lepsze od słabego query.
                
                Odpowiedz WYŁĄCZNIE w formacie JSON, bez żadnego tekstu przed ani po:
                {"queries": ["query 1", "query 2", "query 3"]}
                """.formatted(weakQuery, score);
    }
}