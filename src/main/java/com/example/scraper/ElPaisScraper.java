package com.example.scraper;

import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.util.*;

public class ElPaisScraper {

    public static void main(String[] args) {
        WebDriver driver = null;
        try {
            // Setup ChromeDriver
            System.setProperty("webdriver.chrome.driver", "C:\\Users\\NGS\\Desktop\\drivers\\chromedriver.exe");
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--remote-allow-origins=*");
            driver = new ChromeDriver(options);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

            // Open the El País website
            driver.get("https://elpais.com/");
            System.out.println("Navigated to El País website.");

            // Handle pop-up if present
            handlePopupIfPresent(driver, wait);

            // Navigate to the "Opinión" section
            navigateToOpinionSection(driver, wait);

            // Fetch and process articles
            fetchAndProcessArticles(driver, wait);

        } catch (Exception e) {
            System.out.println("Error during execution: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
                System.out.println("Browser session closed.");
            }
        }
    }

    // Method to handle pop-up if present
    public static void handlePopupIfPresent(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement popUp = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("didomi-notice")));
            System.out.println("Pop-up detected.");
            WebElement acceptButton = popUp.findElement(By.id("didomi-notice-agree-button"));
            wait.until(ExpectedConditions.elementToBeClickable(acceptButton));
            acceptButton.click();
            System.out.println("Pop-up accepted.");
        } catch (TimeoutException e) {
            System.out.println("Pop-up not present or already dismissed.");
        }
    }

    // Method to navigate to "Opinión" section
    public static void navigateToOpinionSection(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement opinionLink = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//a[contains(@href, '/opinion/') or text()='Opinión']")));
            wait.until(ExpectedConditions.elementToBeClickable(opinionLink));
            opinionLink.click();
            System.out.println("Navigated to 'Opinión' section.");
            Thread.sleep(2000); // Add a brief pause for the page to stabilize
        } catch (TimeoutException e) {
            System.out.println("Failed to navigate to 'Opinión' section: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Method to fetch and process articles
    public static void fetchAndProcessArticles(WebDriver driver, WebDriverWait wait) {
        try {
            List<WebElement> articles = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(
                    By.cssSelector(".c_t a")));

            int articleCount = Math.min(articles.size(), 5);

            Map<String, Integer> wordFrequency = new HashMap<>();
            List<String> titles = new ArrayList<>();

            for (int i = 0; i < articleCount; i++) {
                WebElement article = articles.get(i);
                wait.until(ExpectedConditions.visibilityOf(article));

                String title = article.getText();
                titles.add(title);
                System.out.println("Article Title (Spanish): " + title);

                // Translate title to English
                String translatedTitle = translateText(title);
                System.out.println("Translated Title (English): " + translatedTitle);

                Thread.sleep(5000); // Avoid rate-limiting by spacing out API calls

                try {
                    WebElement articleContainer = article.findElement(By.xpath("./ancestor::article"));
                    fetchAndSaveImages(articleContainer, i + 1);
                } catch (NoSuchElementException e) {
                    System.out.println("No figure tags found for article " + (i + 1));
                }

                String[] words = translatedTitle.toLowerCase().split("\\s+");
                for (String word : words) {
                    word = word.replaceAll("[^a-zA-Z]", "");
                    if (word.length() > 2) {
                        wordFrequency.put(word, wordFrequency.getOrDefault(word, 0) + 1);
                    }
                }
            }

            System.out.println("\nRepeated words in titles (translated):");
            wordFrequency.forEach((word, count) -> {
                if (count > 2) {
                    System.out.println("'" + word + "'" + ": " + count + " times");
                }
            });

        } catch (TimeoutException e) {
            System.out.println("Error fetching articles: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error processing articles: " + e.getMessage());
        }
    }

    // Method to translate text from Spanish to English
    public static String translateText(String text) {
        try {
            // Define the new API URL
            String url = "https://nlp-translation.p.rapidapi.com/v1/jsontranslate";

            // Prepare the headers (updated headers)
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/x-www-form-urlencoded");
            headers.put("x-rapidapi-host", "nlp-translation.p.rapidapi.com");
            headers.put("x-rapidapi-key", "201e019651msh985a10e2d9c6b5ep1baf45jsn68a33a101ae9");

            // Prepare the body (request data)
            String requestBody = "from=es&to=en&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

            // Create and send the POST request using HttpURLConnection
            URL apiUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            // Add headers to the request
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            // Send the request body
            connection.getOutputStream().write(requestBody.getBytes(StandardCharsets.UTF_8));

            // Read the response
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // If the response is OK, read the translation result
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = reader.readLine()) != null) {
                    response.append(inputLine);
                }
                reader.close();

                // Log the raw response for debugging
                String responseBody = response.toString();
                System.out.println("API Response: " + responseBody);

                // Try to parse the JSON response
                JSONObject jsonResponse = new JSONObject(responseBody);

                // Check if the response contains translation data
                if (jsonResponse.has("data")) {
                    JSONArray translations = jsonResponse.getJSONArray("data");
                    StringBuilder translatedText = new StringBuilder();
                    for (int i = 0; i < translations.length(); i++) {
                        translatedText.append(translations.getJSONObject(i).getString("translatedText")).append(" | ");
                    }
                    // Remove last separator "|"
                    if (translatedText.length() > 0) {
                        translatedText.setLength(translatedText.length() - 3); // Remove last " | "
                    }
                    return translatedText.toString(); // Return the translated text
                }

                // Handle case where no translation is found in the response
                System.out.println("No translated text found in the response.");
                return text; // Return original text if no translation is found
            } else {
                // Print the error response if the response code is not OK
                System.out.println("Translation failed: Server returned HTTP response code: " + responseCode);
                return text; // Return the original text if translation fails
            }
        } catch (Exception e) {
            System.out.println("Translation failed: " + e.getMessage());
            return text; // Return original text in case of any error
        }
    }

    // Method to fetch and save images from articles
    public static void fetchAndSaveImages(WebElement articleContainer, int articleIndex) {
        try {
            List<WebElement> figures = articleContainer.findElements(By.tagName("figure"));
            if (figures.isEmpty()) {
                System.out.println("No figure tags found for article " + articleIndex);
                return;
            }

            int imageIndex = 1;
            for (WebElement figure : figures) {
                try {
                    WebElement imageElement = figure.findElement(By.tagName("img"));
                    String imageUrl = imageElement.getAttribute("src");
                    saveImage(imageUrl, "article_" + articleIndex + "image" + imageIndex + ".png");
                    System.out.println("Saved image " + imageIndex + " for article " + articleIndex);
                    imageIndex++;
                } catch (NoSuchElementException e) {
                    System.out.println("No img tag in figure for article " + articleIndex);
                }
            }
        } catch (Exception e) {
            System.out.println("Error fetching images for article " + articleIndex + ": " + e.getMessage());
        }
    }

    // Method to save images from URLs
    public static void saveImage(String imageUrl, String fileName) {
        try {
            // Locate the 'output' folder (inside the target directory)
            String outputDirPath = System.getProperty("user.dir") + File.separator + "output";
            File outputDir = new File(outputDirPath);

            // Ensure the directory exists
            if (!outputDir.exists()) {
                throw new IOException("Output folder does not exist: " + outputDirPath);
            }

            // Define the file path inside the output folder
            File outputFile = new File(outputDir, fileName);

            URL url = new URL(imageUrl);
            URLConnection connection = url.openConnection();
            connection.connect();

            try (var inputStream = connection.getInputStream();
                    var outputStream = new FileOutputStream(outputFile)) {

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                System.out.println("Image saved in output folder: " + outputFile.getAbsolutePath());
            }
        } catch (IOException e) {
            System.out.println("Error saving image: " + e.getMessage());
        }
    }
}