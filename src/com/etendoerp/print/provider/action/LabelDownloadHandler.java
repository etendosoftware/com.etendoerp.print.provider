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

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.Map;

import jakarta.mail.internet.MimeUtility;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.client.application.report.ReportingUtils;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.utils.FileUtility;

/**
 * Action handler for downloading generated label PDF files.
 *
 * <p>This handler extends {@link BaseProcessActionHandler} and is invoked by the
 * {@code OBUIAPP_downloadReport} response action to serve a temporary label PDF
 * file to the browser. When the platform calls this handler with
 * {@code mode=DOWNLOAD}, the file is read from the reporting temp folder,
 * streamed to the HTTP response with the appropriate content type and
 * disposition headers, and then deleted.</p>
 *
 * <p>For any other mode the default {@link BaseProcessActionHandler} behaviour
 * is executed.</p>
 *
 * @see SendGeneratedLabelToPrinter
 */
public class LabelDownloadHandler extends BaseProcessActionHandler {

  private static final Logger log = LogManager.getLogger();
  private static final String UTF8 = "UTF-8";
  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String CONTENT_TYPE_PDF = "application/pdf";
  private static final String DEFAULT_FILE_NAME = "label.pdf";
  private static final String DOWNLOAD_MODE = "DOWNLOAD";
  private static final String MSIE_BROWSER = "MSIE";

  /**
   * Entry point for the action handler execution.
   *
   * <p>If {@code mode=DOWNLOAD}, it streams the requested temporary file to the
   * client. Otherwise it delegates to the parent implementation.</p>
   */
  @Override
  public void execute() {
    final HttpServletRequest request = RequestContext.get().getRequest();
    final String mode = request.getParameter("mode");
    if (isDownloadMode(mode)) {
      try {
        doDownload(request);
      } catch (Exception e) {
        log.error("Error downloading label PDF file", e);
      }
      return;
    }
    super.execute();
  }

  /**
   * Default process execution entry point (JSON-based) for
   * {@link BaseProcessActionHandler}.
   *
   * @param parameters
   *     handler parameters provided by the platform
   * @param content
   *     request content
   * @return an empty {@link JSONObject}
   */
  @Override
  protected JSONObject doExecute(Map<String, Object> parameters, String content) {
    return new JSONObject();
  }

  /**
   * Streams a temporary label PDF file to the HTTP response as an attachment
   * and deletes the file afterwards.
   *
   * @param request
   *     current HTTP request containing {@code fileName} and
   *     {@code tmpfileName} parameters
   * @throws IOException
   *     if an I/O error occurs while writing the response
   */
  protected void doDownload(HttpServletRequest request) throws IOException {
    String fileName = request.getParameter("fileName");
    final String tmpFileName = request.getParameter("tmpfileName");

    fileName = resolveFileName(fileName);

    final String tmpDirectory = getTempFolder();
    final File tmpDir = new File(tmpDirectory);
    final File file = new File(tmpDirectory, tmpFileName);

    validateFilePath(tmpDir, file, tmpFileName);
    validateFileExists(file, tmpFileName);

    final FileUtility fileUtil = new FileUtility(tmpDirectory, tmpFileName, false, true);

    try {
      final HttpServletResponse response = getHttpResponse();
      configureResponse(response);

      final String userAgent = request.getHeader("user-agent");
      final String contentDisposition = buildContentDisposition(fileName, userAgent);
      response.setHeader(CONTENT_DISPOSITION, contentDisposition);

      final ServletOutputStream out = response.getOutputStream();
      fileUtil.dumpFile(out);
      out.flush();
    } finally {
      deleteTemporaryFile(file);
    }
  }

  /**
   * Returns the path to the reporting temp folder.
   * Extracted to a protected method for testability.
   *
   * @return the temp folder path
   */
  protected String getTempFolder() {
    return ReportingUtils.getTempFolder();
  }

  /**
   * Returns the current HTTP response from the request context.
   * Extracted to a protected method for testability.
   *
   * @return the HTTP servlet response
   */
  protected HttpServletResponse getHttpResponse() {
    return RequestContext.get().getResponse();
  }

  /**
   * Checks if the given mode corresponds to a download operation.
   *
   * @param mode
   *     the mode parameter from the request
   * @return {@code true} if mode equals {@code "DOWNLOAD"}
   */
  protected boolean isDownloadMode(String mode) {
    return DOWNLOAD_MODE.equals(mode);
  }

  /**
   * Resolves the final file name, using a default if the provided name is
   * {@code null} or blank.
   *
   * @param fileName
   *     the original file name (may be {@code null} or blank)
   * @return the resolved file name
   */
  protected String resolveFileName(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return DEFAULT_FILE_NAME;
    }
    return fileName;
  }

  /**
   * Validates that the file path is within the allowed temporary directory
   * (prevents path traversal).
   *
   * @param tmpDir
   *     the temporary directory
   * @param file
   *     the file to validate
   * @param tmpFileName
   *     the temporary file name (for error messages)
   * @throws IllegalArgumentException
   *     if the file path is outside the temporary directory
   */
  protected void validateFilePath(File tmpDir, File file, String tmpFileName) {
    try {
      final String tmpDirCanonical = tmpDir.getCanonicalPath() + File.separator;
      final String fileCanonical = file.getCanonicalPath();
      if (!fileCanonical.startsWith(tmpDirCanonical)) {
        throw new IllegalArgumentException("Invalid file path: " + tmpFileName);
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot resolve file path: " + tmpFileName, e);
    }
  }

  /**
   * Validates that the file exists and is a regular file.
   *
   * @param file
   *     the file to check
   * @param tmpFileName
   *     the temporary file name (for error messages)
   * @throws IllegalArgumentException
   *     if the file does not exist or is not a regular file
   */
  protected void validateFileExists(File file, String tmpFileName) {
    if (!file.exists() || !file.isFile()) {
      throw new IllegalArgumentException("Temporary file not found: " + tmpFileName);
    }
  }

  /**
   * Configures the HTTP response with the appropriate content type and
   * encoding for a PDF download.
   *
   * @param response
   *     the HTTP response to configure
   */
  protected void configureResponse(HttpServletResponse response) {
    response.setHeader("Content-Type", CONTENT_TYPE_PDF);
    response.setContentType(CONTENT_TYPE_PDF);
    response.setCharacterEncoding(UTF8);
  }

  /**
   * Builds the {@code Content-Disposition} header value based on the browser
   * type.
   *
   * @param fileName
   *     the file name to include in the header
   * @param userAgent
   *     the user agent string from the request
   * @return the Content-Disposition header value
   * @throws IOException
   *     if encoding fails
   */
  protected String buildContentDisposition(String fileName, String userAgent)
      throws IOException {
    if (isMSIEBrowser(userAgent)) {
      return "attachment; filename=\"" + URLEncoder.encode(fileName, UTF8) + "\"";
    }
    return "attachment; filename=\""
        + MimeUtility.encodeWord(fileName, UTF8, "Q") + "\"";
  }

  /**
   * Checks if the user agent indicates an Internet Explorer browser.
   *
   * @param userAgent
   *     the user agent string
   * @return {@code true} if the user agent contains {@code "MSIE"}
   */
  protected boolean isMSIEBrowser(String userAgent) {
    return userAgent != null && userAgent.contains(MSIE_BROWSER);
  }

  /**
   * Deletes the temporary file, logging a warning if deletion fails.
   *
   * @param file
   *     the file to delete
   */
  protected void deleteTemporaryFile(File file) {
    try {
      Files.deleteIfExists(file.toPath());
    } catch (IOException e) {
      log.warn("Temporary file could not be deleted: {}", file.getAbsolutePath(), e);
    }
  }
}
