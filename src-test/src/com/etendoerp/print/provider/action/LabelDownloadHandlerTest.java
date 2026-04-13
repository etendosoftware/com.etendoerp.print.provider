/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.print.provider.action;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openbravo.client.application.report.ReportingUtils;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.utils.FileUtility;

/**
 * Unit tests for the {@link LabelDownloadHandler} class.
 *
 * <p>Tests cover the helper/utility methods of the handler as well as the HTTP
 * entry points ({@code execute()} and {@code doDownload()}) using Mockito
 * static mocks and mock construction to avoid requiring the full Etendo web
 * container.</p>
 */
class LabelDownloadHandlerTest {

  private static final String LABEL_PDF = "label.pdf";
  private static final String PARAM_FILE_NAME = "fileName";
  private static final String PARAM_TMP_FILE_NAME = "tmpfileName";
  private static final String MODE_DOWNLOAD = "DOWNLOAD";
  private static final String HEADER_USER_AGENT = "user-agent";
  private static final String PARAM_MODE = "mode";
  private static final String ATTACHMENT = "attachment";
  private static final String APPLICATION_PDF = "application/pdf";
  private static final String MOZILLA_5_0 = "Mozilla/5.0";
  private static final String PASSWORD_LOCATION = "../etc/passwd";
  private static final String MODZILLA_COMPATIBILITY = "Mozilla/4.0 (compatible; MSIE 10.0)";
  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String CONTENT = "content";

  /**
   * Verifies that {@code isDownloadMode} returns {@code true} when the mode
   * parameter is exactly {@code "DOWNLOAD"}.
   */
  @Test
  void isDownloadModeReturnsTrueForDownload() {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    assertThat(handler.isDownloadMode(MODE_DOWNLOAD), is(true));
  }

  /**
   * Verifies that {@code isDownloadMode} returns {@code false} for a null value.
   */
  @Test
  void isDownloadModeReturnsFalseForNull() {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    assertThat(handler.isDownloadMode(null), is(false));
  }

  /**
   * Verifies that {@code isDownloadMode} returns {@code false} for an arbitrary
   * string that is not {@code "DOWNLOAD"}.
   */
  @Test
  void isDownloadModeReturnsFalseForOtherMode() {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    assertThat(handler.isDownloadMode("EDIT"), is(false));
  }

  /**
   * Verifies that {@code isDownloadMode} is case-sensitive and returns
   * {@code false} for lowercase {@code "download"}.
   */
  @Test
  void isDownloadModeIsCaseSensitive() {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    assertThat(handler.isDownloadMode("download"), is(false));
  }

  // ─── resolveFileName ──────────────────────────────────────────────────

  /**
   * Verifies that a non-blank file name is returned unchanged.
   */
  @Test
  void resolveFileNameReturnsProvidedName() {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    assertThat(handler.resolveFileName("report.pdf"), is("report.pdf"));
  }

  /**
   * Verifies that a null file name is replaced by the default {@code "label.pdf"}.
   */
  @Test
  void resolveFileNameReturnsDefaultForNull() {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    assertThat(handler.resolveFileName(null), is(LABEL_PDF));
  }

  /**
   * Verifies that an empty string is replaced by the default {@code "label.pdf"}.
   */
  @Test
  void resolveFileNameReturnsDefaultForEmpty() {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    assertThat(handler.resolveFileName(""), is(LABEL_PDF));
  }

  // ─── validateFilePath ─────────────────────────────────────────────────

  /**
   * Verifies that a valid file inside the temp directory passes validation
   * without throwing.
   */
  @Test
  void validateFilePathAcceptsFileInsideTmpDir(@TempDir File tmpDir) throws Exception {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    File file = new File(tmpDir, LABEL_PDF);
    Files.createFile(file.toPath());

    assertDoesNotThrow(() -> handler.validateFilePath(tmpDir, file, LABEL_PDF));
  }

  /**
   * Verifies that a file with a path-traversal component ({@code ../}) is
   * rejected with an {@link IllegalArgumentException}.
   */
  @Test
  void validateFilePathRejectsTraversalAttempt(@TempDir File tmpDir) {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    File file = new File(tmpDir, PASSWORD_LOCATION);

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> handler.validateFilePath(tmpDir, file, PASSWORD_LOCATION));
    assertThat(ex.getMessage(), containsString("Invalid file path"));
  }

  /**
   * Verifies that a file located completely outside the temp directory is
   * rejected.
   */
  @Test
  void validateFilePathRejectsFileOutsideDir(@TempDir File tmpDir) {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    File outsideFile = new File(tmpDir.getParentFile(), "outside.pdf");

    assertThrows(IllegalArgumentException.class,
        () -> handler.validateFilePath(tmpDir, outsideFile, "outside.pdf"));
  }

  // ─── validateFileExists ───────────────────────────────────────────────

  /**
   * Verifies that an existing regular file passes the existence check.
   */
  @Test
  void validateFileExistsAcceptsExistingFile(@TempDir File tmpDir) throws Exception {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    File file = new File(tmpDir, LABEL_PDF);
    Files.createFile(file.toPath());

    assertDoesNotThrow(() -> handler.validateFileExists(file, LABEL_PDF));
  }

  /**
   * Verifies that a non-existent file causes an {@link IllegalArgumentException}.
   */
  @Test
  void validateFileExistsRejectsNonExistent(@TempDir File tmpDir) {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    File file = new File(tmpDir, "nope.pdf");

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> handler.validateFileExists(file, "nope.pdf"));
    assertThat(ex.getMessage(), containsString("Temporary file not found"));
  }

  /**
   * Verifies that a directory (not a regular file) is rejected by the existence
   * check.
   */
  @Test
  void validateFileExistsRejectsDirectory(@TempDir File tmpDir) {
    LabelDownloadHandler handler = new LabelDownloadHandler();

    assertThrows(IllegalArgumentException.class,
        () -> handler.validateFileExists(tmpDir, "dir"));
  }

  // ─── buildContentDisposition ──────────────────────────────────────────

  /**
   * Verifies that the Content-Disposition header for a standard (non-MSIE)
   * browser contains the file name as an attachment with MIME-word encoding.
   */
  @Test
  void buildContentDispositionForStandardBrowser() throws Exception {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    String cd = handler.buildContentDisposition(LABEL_PDF, MOZILLA_5_0);
    assertThat(cd, containsString(ATTACHMENT));
    assertThat(cd, containsString(LABEL_PDF));
  }

  /**
   * Verifies that the Content-Disposition header for MSIE uses URL-encoded
   * file names.
   */
  @Test
  void buildContentDispositionForMSIE() throws Exception {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    String cd = handler.buildContentDisposition("my label.pdf", MODZILLA_COMPATIBILITY);
    assertThat(cd, containsString(ATTACHMENT));
    assertThat(cd, containsString("my+label.pdf"));
  }

  /**
   * Verifies that a null user-agent is treated as a non-MSIE browser and does
   * not throw.
   */
  @Test
  void buildContentDispositionHandlesNullUserAgent() throws Exception {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    String cd = handler.buildContentDisposition(LABEL_PDF, null);
    assertThat(cd, containsString(ATTACHMENT));
    assertThat(cd, containsString(LABEL_PDF));
  }

  // ─── isMSIEBrowser ───────────────────────────────────────────────────

  /**
   * Verifies MSIE detection when user agent contains "MSIE".
   */
  @Test
  void isMSIEBrowserReturnsTrueForIE() {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    assertThat(handler.isMSIEBrowser(MODZILLA_COMPATIBILITY), is(true));
  }

  /**
   * Verifies MSIE detection returns false for Chrome.
   */
  @Test
  void isMSIEBrowserReturnsFalseForChrome() {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    assertThat(handler.isMSIEBrowser("Mozilla/5.0 Chrome/120.0"), is(false));
  }

  /**
   * Verifies MSIE detection returns false for null user-agent.
   */
  @Test
  void isMSIEBrowserReturnsFalseForNull() {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    assertThat(handler.isMSIEBrowser(null), is(false));
  }

  // ─── deleteTemporaryFile ──────────────────────────────────────────────

  /**
   * Verifies that an existing file is deleted successfully.
   */
  @Test
  void deleteTemporaryFileDeletesExistingFile(@TempDir File tmpDir) throws Exception {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    File file = new File(tmpDir, "temp.pdf");
    Files.createFile(file.toPath());

    assertThat(file.exists(), is(true));
    handler.deleteTemporaryFile(file);
    assertThat(file.exists(), is(false));
  }

  /**
   * Verifies that deleting a non-existent file does not throw an exception.
   */
  @Test
  void deleteTemporaryFileHandlesNonExistentGracefully(@TempDir File tmpDir) {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    File file = new File(tmpDir, "ghost.pdf");

    assertDoesNotThrow(() -> handler.deleteTemporaryFile(file));
  }

  // ─── doExecute ────────────────────────────────────────────────────────

  /**
   * Verifies that {@code doExecute} returns an empty JSON object.
   */
  @Test
  void doExecuteReturnsEmptyJsonObject() {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    JSONObject result = handler.doExecute(Map.of(), "{}");
    assertThat(result, is(not(nullValue())));
    assertThat(result.length(), is(0));
  }

  // ─── configureResponse ───────────────────────────────────────────────

  /**
   * Verifies that {@code configureResponse} sets the correct Content-Type,
   * content type and character encoding on the HTTP response.
   */
  @Test
  void configureResponseSetsPdfHeaders() {
    LabelDownloadHandler handler = new LabelDownloadHandler();
    HttpServletResponse response = mock(HttpServletResponse.class);

    handler.configureResponse(response);

    verify(response).setHeader("Content-Type", APPLICATION_PDF);
    verify(response).setContentType(APPLICATION_PDF);
    verify(response).setCharacterEncoding("UTF-8");
  }

  // ─── execute ──────────────────────────────────────────────────────────

  /**
   * Verifies that {@code execute()} delegates to {@code doDownload()} when
   * the request contains {@code mode=DOWNLOAD}.
   */
  @Test
  void executeCallsDoDownloadWhenModeIsDownload() throws Exception {
    LabelDownloadHandler spy = Mockito.spy(new LabelDownloadHandler());

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter(PARAM_MODE)).thenReturn(MODE_DOWNLOAD);

    RequestContext rc = mock(RequestContext.class);
    when(rc.getRequest()).thenReturn(request);

    try (MockedStatic<RequestContext> sRc = Mockito.mockStatic(RequestContext.class)) {
      sRc.when(RequestContext::get).thenReturn(rc);
      doNothing().when(spy).doDownload(request);

      spy.execute();

      verify(spy).doDownload(request);
    }
  }

  /**
   * Verifies that {@code execute()} catches exceptions thrown by
   * {@code doDownload()} and does not propagate them.
   */
  @Test
  void executeHandlesExceptionFromDoDownloadGracefully() throws Exception {
    LabelDownloadHandler spy = Mockito.spy(new LabelDownloadHandler());

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter(PARAM_MODE)).thenReturn(MODE_DOWNLOAD);

    RequestContext rc = mock(RequestContext.class);
    when(rc.getRequest()).thenReturn(request);

    try (MockedStatic<RequestContext> sRc = Mockito.mockStatic(RequestContext.class)) {
      sRc.when(RequestContext::get).thenReturn(rc);
      doThrow(new IOException("simulated error")).when(spy).doDownload(request);

      assertDoesNotThrow(spy::execute);
    }
  }

  /**
   * Verifies that {@code execute()} does NOT call {@code doDownload()} when
   * the mode is not {@code "DOWNLOAD"}.
   */
  @Test
  void executeDoesNotCallDoDownloadForOtherModes() throws Exception {
    LabelDownloadHandler spy = Mockito.spy(new LabelDownloadHandler());

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter(PARAM_MODE)).thenReturn("EDIT");

    RequestContext rc = mock(RequestContext.class);
    when(rc.getRequest()).thenReturn(request);

    try (MockedStatic<RequestContext> sRc = Mockito.mockStatic(RequestContext.class)) {
      sRc.when(RequestContext::get).thenReturn(rc);
      // super.execute() will also need RequestContext, but we just verify
      // doDownload is never called; let any super.execute NPE be swallowed
      try {
        spy.execute();
      } catch (Exception ignored) {
        // super.execute() may fail in unit test context – that's OK
      }

      verify(spy, never()).doDownload(any(HttpServletRequest.class));
    }
  }

  /**
   * Verifies that {@code execute()} does NOT call {@code doDownload()} when
   * the mode is null.
   */
  @Test
  void executeDoesNotCallDoDownloadForNullMode() throws Exception {
    LabelDownloadHandler spy = Mockito.spy(new LabelDownloadHandler());

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter(PARAM_MODE)).thenReturn(null);

    RequestContext rc = mock(RequestContext.class);
    when(rc.getRequest()).thenReturn(request);

    try (MockedStatic<RequestContext> sRc = Mockito.mockStatic(RequestContext.class)) {
      sRc.when(RequestContext::get).thenReturn(rc);
      try {
        spy.execute();
      } catch (Exception ignored) {
        // super.execute() may fail in unit test context
      }

      verify(spy, never()).doDownload(any(HttpServletRequest.class));
    }
  }

  // ─── doDownload ──────────────────────────────────────────────────────

  /**
   * Creates a spy of {@link LabelDownloadHandler} that overrides
   * {@code getTempFolder()} and {@code getHttpResponse()} to avoid static
   * dependencies on {@link ReportingUtils} and {@link RequestContext}.
   */
  private LabelDownloadHandler spyWithTmpDir(File tmpDir, HttpServletResponse response) {
    LabelDownloadHandler spy = Mockito.spy(new LabelDownloadHandler());
    Mockito.doReturn(tmpDir.getAbsolutePath()).when(spy).getTempFolder();
    Mockito.doReturn(response).when(spy).getHttpResponse();
    return spy;
  }

  /**
   * Creates a spy that only overrides {@code getTempFolder()} (when no HTTP
   * response is needed, e.g. for validation-failure tests).
   */
  private LabelDownloadHandler spyWithTmpDirOnly(File tmpDir) {
    LabelDownloadHandler spy = Mockito.spy(new LabelDownloadHandler());
    Mockito.doReturn(tmpDir.getAbsolutePath()).when(spy).getTempFolder();
    return spy;
  }

  /**
   * Verifies that {@code doDownload()} reads the file from the temp folder,
   * configures the response, streams the file, and deletes it afterwards.
   */
  @Test
  void doDownloadStreamsFileAndDeletesIt(@TempDir File tmpDir) throws Exception {
    // Create a real temp file to be served
    File pdfFile = new File(tmpDir, "test-label.pdf");
    Files.write(pdfFile.toPath(), "fake pdf content".getBytes(StandardCharsets.UTF_8));

    // Mock request parameters
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter(PARAM_FILE_NAME)).thenReturn("my-label.pdf");
    when(request.getParameter(PARAM_TMP_FILE_NAME)).thenReturn("test-label.pdf");
    when(request.getHeader(HEADER_USER_AGENT)).thenReturn(MOZILLA_5_0);

    // Mock response
    HttpServletResponse response = mock(HttpServletResponse.class);
    ServletOutputStream out = mock(ServletOutputStream.class);
    when(response.getOutputStream()).thenReturn(out);

    LabelDownloadHandler spy = spyWithTmpDir(tmpDir, response);

    try (MockedConstruction<FileUtility> mFileUtil = Mockito.mockConstruction(FileUtility.class)) {
      spy.doDownload(request);

      // Verify response was configured with PDF headers
      verify(response).setHeader("Content-Type", APPLICATION_PDF);
      verify(response).setContentType(APPLICATION_PDF);
      verify(response).setCharacterEncoding("UTF-8");

      // Verify Content-Disposition header was set
      verify(response).setHeader(Mockito.eq(CONTENT_DISPOSITION),
          Mockito.contains(ATTACHMENT));

      // Verify file was streamed via FileUtility
      assertThat(mFileUtil.constructed().size(), is(1));
      FileUtility mockFileUtil = mFileUtil.constructed().get(0);
      verify(mockFileUtil).dumpFile(out);

      // Verify output was flushed
      verify(out).flush();

      // Verify temporary file was deleted
      assertThat(pdfFile.exists(), is(false));
    }
  }

  /**
   * Verifies that {@code doDownload()} uses the default file name when
   * the fileName parameter is null.
   */
  @Test
  void doDownloadUsesDefaultFileNameWhenNull(@TempDir File tmpDir) throws Exception {
    File pdfFile = new File(tmpDir, "uuid-file.pdf");
    Files.write(pdfFile.toPath(), CONTENT.getBytes(StandardCharsets.UTF_8));

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter(PARAM_FILE_NAME)).thenReturn(null);
    when(request.getParameter(PARAM_TMP_FILE_NAME)).thenReturn("uuid-file.pdf");
    when(request.getHeader(HEADER_USER_AGENT)).thenReturn(MOZILLA_5_0);

    HttpServletResponse response = mock(HttpServletResponse.class);
    ServletOutputStream out = mock(ServletOutputStream.class);
    when(response.getOutputStream()).thenReturn(out);

    LabelDownloadHandler spy = spyWithTmpDir(tmpDir, response);

    try (MockedConstruction<FileUtility> ignored = Mockito.mockConstruction(FileUtility.class)) {
      spy.doDownload(request);

      // Verify Content-Disposition uses the default file name "label.pdf"
      verify(response).setHeader(Mockito.eq(CONTENT_DISPOSITION),
          Mockito.contains(LABEL_PDF));
    }
  }

  /**
   * Verifies that {@code doDownload()} throws an {@link IllegalArgumentException}
   * when the tmpfileName contains a path traversal sequence.
   */
  @Test
  void doDownloadRejectsPathTraversal(@TempDir File tmpDir) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter(PARAM_FILE_NAME)).thenReturn(LABEL_PDF);
    when(request.getParameter(PARAM_TMP_FILE_NAME)).thenReturn(PASSWORD_LOCATION);

    LabelDownloadHandler spy = spyWithTmpDirOnly(tmpDir);

    assertThrows(IllegalArgumentException.class, () -> spy.doDownload(request));
  }

  /**
   * Verifies that {@code doDownload()} throws an {@link IllegalArgumentException}
   * when the specified file does not exist.
   */
  @Test
  void doDownloadRejectsNonExistentFile(@TempDir File tmpDir) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter(PARAM_FILE_NAME)).thenReturn(LABEL_PDF);
    when(request.getParameter(PARAM_TMP_FILE_NAME)).thenReturn("no-such-file.pdf");

    LabelDownloadHandler spy = spyWithTmpDirOnly(tmpDir);

    assertThrows(IllegalArgumentException.class, () -> spy.doDownload(request));
  }

  /**
   * Verifies that {@code doDownload()} deletes the temporary file even if the
   * output stream throws an exception during flush.
   */
  @Test
  void doDownloadDeletesFileEvenOnStreamError(@TempDir File tmpDir) throws Exception {
    File pdfFile = new File(tmpDir, "error-label.pdf");
    Files.write(pdfFile.toPath(), CONTENT.getBytes(StandardCharsets.UTF_8));

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter(PARAM_FILE_NAME)).thenReturn(LABEL_PDF);
    when(request.getParameter(PARAM_TMP_FILE_NAME)).thenReturn("error-label.pdf");
    when(request.getHeader(HEADER_USER_AGENT)).thenReturn(MOZILLA_5_0);

    HttpServletResponse response = mock(HttpServletResponse.class);
    ServletOutputStream out = mock(ServletOutputStream.class);
    when(response.getOutputStream()).thenReturn(out);
    doThrow(new IOException("stream broken")).when(out).flush();

    LabelDownloadHandler spy = spyWithTmpDir(tmpDir, response);

    try (MockedConstruction<FileUtility> ignored = Mockito.mockConstruction(FileUtility.class)) {
      assertThrows(IOException.class, () -> spy.doDownload(request));

      // File must be deleted even though the stream threw an exception (finally block)
      assertThat(pdfFile.exists(), is(false));
    }
  }

  /**
   * Verifies that {@code doDownload()} sets the MSIE-style Content-Disposition
   * header when the user-agent indicates Internet Explorer.
   */
  @Test
  void doDownloadSetsIEContentDisposition(@TempDir File tmpDir) throws Exception {
    File pdfFile = new File(tmpDir, "ie-label.pdf");
    Files.write(pdfFile.toPath(), CONTENT.getBytes(StandardCharsets.UTF_8));

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter(PARAM_FILE_NAME)).thenReturn("my label.pdf");
    when(request.getParameter(PARAM_TMP_FILE_NAME)).thenReturn("ie-label.pdf");
    when(request.getHeader(HEADER_USER_AGENT)).thenReturn(MODZILLA_COMPATIBILITY);

    HttpServletResponse response = mock(HttpServletResponse.class);
    ServletOutputStream out = mock(ServletOutputStream.class);
    when(response.getOutputStream()).thenReturn(out);

    LabelDownloadHandler spy = spyWithTmpDir(tmpDir, response);

    try (MockedConstruction<FileUtility> ignored = Mockito.mockConstruction(FileUtility.class)) {
      spy.doDownload(request);

      // MSIE should URL-encode spaces as +
      verify(response).setHeader(Mockito.eq(CONTENT_DISPOSITION),
          Mockito.contains("my+label.pdf"));
    }
  }
}
