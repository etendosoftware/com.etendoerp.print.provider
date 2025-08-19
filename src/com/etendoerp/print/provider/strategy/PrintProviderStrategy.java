package com.etendoerp.print.provider.strategy;

import java.io.File;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.api.PrinterDTO;
import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.TemplateLine;

/**
 * Provider-agnostic contract that any printing connector must implement.
 *
 * <p>The strategy encapsulates the interaction with a concrete print backend
 * (e.g., PrintNode, CUPS, vendor cloud). It is used by generic Actions such as:
 * <ul>
 *   <li><b>UpdatePrinters</b> → calls {@link #fetchPrinters(Provider)} to sync devices</li>
 *   <li><b>SendGeneratedLabelToPrinter</b> → calls
 *       {@link #generateLabel(Provider, Table, String, TemplateLine, JSONObject)} and
 *       {@link #sendToPrinter(Provider, Printer, File)}</li>
 * </ul>
 *
 * <h3>General requirements</h3>
 * <ul>
 *   <li>Implementations must be stateless and thread-safe (no shared mutable state).</li>
 *   <li>Fail fast with meaningful {@link PrintProviderException} messages for
 *       configuration/auth/network issues.</li>
 *   <li>Honor organization and client scoping carried by the {@link Provider} DAL entity.</li>
 * </ul>
 *
 * <h3>Entity mapping conventions</h3>
 * <ul>
 *   <li>{@link PrinterDTO#id()} → stored in {@code ETPP_Printer.value} (external printer id).</li>
 *   <li>{@link PrinterDTO#name()} → stored in {@code ETPP_Printer.name}.</li>
 *   <li>{@link PrinterDTO#isDefault()} → stored in {@code ETPP_Printer.isDefault}.</li>
 * </ul>
 *
 * <h3>Label file lifecycle</h3>
 * <ul>
 *   <li>{@code generateLabel(...)} should return a file ready to be sent.</li>
 *   <li>Prefer creating a <i>temporary</i> file under {@code java.io.tmpdir}; callers
 *       may delete it after {@code sendToPrinter(...)} completes.</li>
 *   <li>If your backend supports Base64 payloads, you may still generate a file and
 *       encode its bytes in {@code sendToPrinter}.</li>
 * </ul>
 *
 * <h3>Error signaling</h3>
 * <ul>
 *   <li>Throw {@link PrintProviderException} for provider/API/transport/format errors.</li>
 *   <li>Do not throw unchecked exceptions unless it is truly unrecoverable.</li>
 * </ul>
 *
 * <h3>Minimal usage flow</h3>
 * <pre>{@code
 * PrintProviderStrategy s = ... // resolved via ProviderStrategyResolver
 * List<PrinterDTO> printers = s.fetchPrinters(provider);
 * File label = s.generateLabel(provider, table, recordId, templateLine, params);
 * String jobId = s.sendToPrinter(provider, selectedPrinter, label);
 * }</pre>
 */
public interface PrintProviderStrategy {

  /**
   * Retrieves the current list of printers from the provider for the given org/client context.
   *
   * <p>The result is used to upsert {@code ETPP_Printer} rows. Implementations should
   * authenticate using credentials found in {@link Provider} and call the provider’s
   * “list printers” endpoint or mechanism.</p>
   *
   * @param provider
   *   DAL entity with configuration (API key, endpoints, org/client).
   * @return immutable list of printers (never {@code null}); may be empty if none are available.
   * @throws PrintProviderException
   *   if authentication fails, the endpoint is unreachable, the response cannot be parsed,
   *   or the provider rejects the request.
   */
  List<PrinterDTO> fetchPrinters(Provider provider) throws PrintProviderException;

  /**
   * Renders a label file for the target record using the selected template line.
   *
   * <p>Typical implementations will resolve and run a Jasper report or a template engine
   * to produce a PDF/ZPL/PNG file. The returned file must exist and be readable.</p>
   *
   * @param provider
   *   print provider configuration (may carry options influencing content type).
   * @param table
   *   AD table of the record to print (e.g., {@code M_InOut}).
   * @param recordId
   *   primary key of the record to print.
   * @param templateLineRef
   *   selected {@code ETPP_TemplateLine} that contains the template location and flags.
   * @param parameters
   *   raw process parameters (may include user-selected options like copies, language, etc.).
   * @return a {@link File} pointing to the generated label (temporary file recommended).
   * @throws PrintProviderException
   *   if the template cannot be loaded, rendering fails, or the output cannot be written.
   */
  File generateLabel(Provider provider,
      Table table,
      String recordId,
      TemplateLine templateLineRef,
      JSONObject parameters) throws PrintProviderException;

  /**
   * Sends the generated label to the provider’s print queue and returns a provider job identifier.
   *
   * <p>Implementations should pick the appropriate content type (e.g., {@code pdf_base64})
   * and submit the job to the provider’s API or spooler using credentials from {@link Provider}.</p>
   *
   * @param provider
   *   print provider configuration (endpoints, API key, org/client).
   * @param printer
   *   DAL {@code ETPP_Printer} row indicating the destination device; its {@code value}
   *   stores the provider’s external printer id.
   * @param labelFile
   *   file previously created by {@link #generateLabel}; must exist and be readable.
   * @return provider job id (stringified) if available; otherwise a non-empty fallback summary.
   * @throws PrintProviderException
   *   if authentication fails, the destination printer is not accepted, the request is rejected,
   *   there is a transport error, or the response cannot be interpreted.
   */
  String sendToPrinter(Provider provider,
      Printer printer,
      File labelFile) throws PrintProviderException;
}