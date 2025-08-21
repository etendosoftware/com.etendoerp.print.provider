package com.etendoerp.print.provider.strategy;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.api.PrinterDTO;
import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.TemplateLine;

public abstract class PrintProviderStrategy {

  public List<PrinterDTO> fetchPrinters(Provider provider) throws PrintProviderException {
    return Collections.emptyList(); // default: no printers
  }

  public File generateLabel(Provider provider,
      Table table,
      String recordId,
      TemplateLine templateLineRef,
      JSONObject parameters) throws PrintProviderException {
    throw new UnsupportedOperationException("generateLabel not implemented by " + getClass().getSimpleName());
  }

  public String sendToPrinter(Provider provider,
      Printer printer,
      File labelFile) throws PrintProviderException {
    throw new UnsupportedOperationException("sendToPrinter not implemented by " + getClass().getSimpleName());
  }
}