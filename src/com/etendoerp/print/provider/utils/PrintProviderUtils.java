package com.etendoerp.print.provider.utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.slf4j.Logger;

import com.etendoerp.print.provider.api.PrintProviderException;

/**
 * Utility class for print provider operations.
 *
 * <p>This class provides static methods for building HTTP requests and sending
 * them using a {@link HttpClient}. It also provides methods for encoding files
 * as base 64 strings and extracting print job IDs from response bodies.</p>
 */
public final class PrintProviderUtils {

  /**
   * Private constructor to prevent instantiation of this utility class.
   * Throws UnsupportedOperationException if called.
   */
  private PrintProviderUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static final String MIME_JSON = "application/json";

  /**
   * Builds a new {@link HttpClient} with the given connection timeout.
   *
   * <p>The returned client has a default cookie manager and no redirect policy.
   * </p>
   *
   * @param connectTimeoutSeconds
   *     Connection timeout in seconds.
   * @return A new {@link HttpClient}.
   */
  public static HttpClient newHttpClient(int connectTimeoutSeconds) {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
        .build();
  }

  /**
   * Builds a new {@link HttpRequest} to perform a GET request to the given URL,
   * with the given basic auth and request timeout.
   *
   * <p>The request is configured to accept JSON responses and has a basic auth
   * header with the given credentials. The request timeout is set to the given
   * number of seconds.
   * </p>
   *
   * @param url
   *     The URL of the request.
   * @param basicAuth
   *     The basic auth credentials to use. The password is
   *     Base64-encoded.
   * @param requestTimeoutSeconds
   *     The request timeout in seconds.
   * @return A new {@link HttpRequest}.
   */
  public static HttpRequest buildJsonGet(String url, String basicAuth, int requestTimeoutSeconds) {
    return HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(requestTimeoutSeconds))
        .header("Authorization", "Basic " + basicAuth)
        .header("Accept", MIME_JSON)
        .GET()
        .build();
  }

  /**
   * Builds a new {@link HttpRequest} to perform a POST request to the given URL,
   * with the given basic auth and request timeout, and the given JSON body.
   *
   * <p>The request is configured to accept JSON responses and has a basic auth
   * header with the given credentials. The request timeout is set to the given
   * number of seconds. The JSON body of the request is set to the given string,
   * encoded in UTF-8.
   * </p>
   *
   * @param url
   *     The URL of the request.
   * @param basicAuth
   *     The basic auth credentials to use. The password is
   *     Base64-encoded.
   * @param requestTimeoutSeconds
   *     The request timeout in seconds.
   * @param body
   *     The JSON body of the request, as a UTF-8 string.
   * @return A new {@link HttpRequest}.
   */
  public static HttpRequest buildJsonPost(String url, String basicAuth, int requestTimeoutSeconds, String body) {
    return HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(requestTimeoutSeconds))
        .header("Authorization", "Basic " + basicAuth)
        .header("Accept", MIME_JSON)
        .header("Content-Type", MIME_JSON)
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();
  }

  /**
   * Sends the given request using the given client and returns the response.
   *
   * <p>The request is sent using the given client and the response is returned
   * as a string, decoded from UTF-8. If the request is interrupted, an
   * {@link PrintProviderException} is thrown with a message obtained from the
   * given i18n key, and the thread is re-interrupted. If the request fails with
   * an {@link IOException}, an {@link PrintProviderException} is thrown with the
   * same message and cause.
   * </p>
   *
   * @param client
   *     The client to use.
   * @param req
   *     The request to send.
   * @param i18nOnInterruptKey
   *     The i18n key to use to format the message when
   *     the request is interrupted.
   * @return The response of the request.
   * @throws PrintProviderException
   *     If the request is interrupted or fails.
   */
  public static HttpResponse<String> send(HttpClient client, HttpRequest req, String i18nOnInterruptKey)
      throws PrintProviderException {
    try {
      return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      String msg = String.format(OBMessageUtils.getI18NMessage(i18nOnInterruptKey), ie.getMessage());
      throw new PrintProviderException(msg, ie);
    } catch (IOException io) {
      throw new PrintProviderException(io.getMessage(), io);
    }
  }

  /**
   * Encodes the given API key as a "Basic" authorization header value.
   *
   * <p>The method takes the given API key, appends a colon ({@code :}) to it,
   * converts it to a byte array using UTF-8, and encodes it to a base 64 string
   * using the {@link java.util.Base64#getEncoder()}. The resulting string is
   * suitable to be used as the value of the {@code Authorization} header in a
   * "Basic" HTTP request.
   * </p>
   *
   * @param apiKey
   *     The API key to encode. Must not be {@code null} or empty.
   * @return The encoded API key as a base 64 string.
   */
  public static String buildBasicAuth(final String apiKey) {
    final String raw = apiKey + ":";
    return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Encodes the given file as a base 64 string.
   *
   * <p>The method takes the given file, reads its entire contents into a byte
   * array, and encodes it
   * to a base 64 string using the {@link java.util.Base64#getEncoder()}. The
   * resulting string is suitable to be used as the value of a request parameter
   * or body in an HTTP request.
   * </p>
   *
   * @param file
   *     The file to encode. Must not be {@code null}.
   * @return The encoded file as a base 64 string.
   * @throws IOException
   *     If there is an error reading the file.
   */
  public static String encodeFileToBase64(File file) throws IOException {
    return Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
  }

  /**
   * Extracts the print job ID from the given response body, or a preview of it
   * if the response is not a valid JSON object.
   * <p>
   * The method takes the given response body, trims it, and checks if it
   * matches the regular expression {@code "\\d+"}. If it does, it returns the
   * response body as-is. Otherwise, it tries to parse the response body as a
   * JSON object. If the JSON object contains the keys "id", "jobId", or
   * "printJobId", the method returns the value of the first key found. If the
   * JSON object does not contain any of the above keys, the method returns a
   * truncated version of the response body, limited to the given maximum
   * length.
   * </p>
   * <p>
   * If the response body is not a valid JSON object, the method logs a warning
   *
   * @param rawBody
   *     The response body to process.
   * @param log
   *     The logger to use for warnings.
   * @param previewMaxLen
   *     The maximum length of the preview to return.
   * @return The print job ID, or a preview of the response body if it is not a
   *     valid JSON object.
   */
  public static String extractJobIdOrPreview(String rawBody, Logger log, int previewMaxLen) {
    final String raw = rawBody == null ? "" : rawBody.trim();
    if (raw.matches("\\d+")) {
      return raw;
    }
    try {
      final JSONObject job = new JSONObject(raw);
      if (job.has("id")) return String.valueOf(job.opt("id"));
      if (job.has("jobId")) return String.valueOf(job.opt("jobId"));
      if (job.has("printJobId")) return String.valueOf(job.opt("printJobId"));
      return truncate(raw, previewMaxLen);
    } catch (JSONException je) {
      if (log != null && log.isWarnEnabled()) {
        log.warn("Unexpected non-JSON response from print provider: {}", truncate(raw, Math.max(previewMaxLen, 300)));
      }
      return truncate(raw, previewMaxLen);
    }
  }

  /**
   * Truncates the given string to the given maximum length.
   *
   * <p>
   * If the given string is {@code null}, the method returns {@code null}.
   * Otherwise, if the string is shorter than the given maximum length, the
   * method returns the string as-is. Otherwise, the method returns a
   * substring of the given string, starting at the beginning and ending at
   * the given maximum length, followed by three dots ("...").
   * </p>
   *
   * @param s
   *     The string to truncate.
   * @param max
   *     The maximum length of the string.
   * @return The truncated string, or {@code null} if the given string is
   *     {@code null}.
   */
  public static String truncate(final String s, final int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max) + "...";
  }
}