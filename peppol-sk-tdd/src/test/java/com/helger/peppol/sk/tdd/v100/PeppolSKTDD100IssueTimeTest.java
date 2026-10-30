/*
 * Copyright (C) 2026 Philip Helger
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.peppol.sk.tdd.v100;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.function.Consumer;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.helger.base.io.stream.StreamHelper;
import com.helger.diagnostics.error.IError;
import com.helger.io.resource.ClassPathResource;
import com.helger.io.resource.inmemory.ReadableResourceString;
import com.helger.peppol.sk.tdd.codelist.ESKTDDDocumentScope;
import com.helger.peppol.sk.tdd.codelist.ESKTDDReporterRole;
import com.helger.peppol.sk.tdd.codelist.ESKTDDTaxDataTypeCode;
import com.helger.peppol.sk.tdd.jaxb.CPeppolSKTDD;
import com.helger.peppol.sk.tdd.jaxb.PeppolSKTDD100Marshaller;
import com.helger.peppol.sk.tdd.v2026_04_14.TaxDataType;
import com.helger.peppol.sk.tdd.validate.PeppolSKTDDValidator;
import com.helger.peppolid.factory.IIdentifierFactory;
import com.helger.peppolid.factory.PeppolIdentifierFactory;
import com.helger.phive.api.result.ValidationResultList;
import com.helger.ubl21.CUBL21;
import com.helger.ubl21.UBL21Marshaller;
import com.helger.xml.XMLHelper;
import com.helger.xml.serialize.read.DOMReader;

import oasis.names.specification.ubl.schema.xsd.creditnote_21.CreditNoteType;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;

/**
 * Test that the issue time of an Invoice or CreditNote is taken over into
 * ReportedDocument/cbc:IssueTime as given: a time without offset stays without offset and all
 * fractional digits of the seconds are kept.
 *
 * @author Philip Helger
 */
public final class PeppolSKTDD100IssueTimeTest
{
  private static final String INVOICE_PATH = "external/invoice/good/sk-issue-time.xml";
  private static final String CREDIT_NOTE_PATH = "external/creditnote/good/sk-issue-time.xml";
  private static final String FILE_ISSUE_TIME = "10:15:30";

  private static String _readWithIssueTime (final String sPath, final String sIssueTime)
  {
    final String sXML = StreamHelper.getAllBytesAsString (new ClassPathResource (sPath), StandardCharsets.UTF_8);
    final String sFileElement = "<cbc:IssueTime>" + FILE_ISSUE_TIME + "</cbc:IssueTime>";
    assertTrue (sXML.contains (sFileElement));
    return sXML.replace (sFileElement, "<cbc:IssueTime>" + sIssueTime + "</cbc:IssueTime>");
  }

  /**
   * Build and validate the TDD.
   *
   * @return The text of ReportedDocument/cbc:IssueTime as written in the TDD
   */
  private static String _buildAndGetIssueTime (final Consumer <PeppolSKTDD100ReportedTransactionBuilder> aInit)
  {
    final IIdentifierFactory aIF = PeppolIdentifierFactory.INSTANCE;

    final TaxDataType aTDD = new PeppolSKTDD100Builder ().taxDataTypeCode (ESKTDDTaxDataTypeCode.SUBMIT)
                                                         .documentScope (ESKTDDDocumentScope.DOMESTIC)
                                                         .reporterRole (ESKTDDReporterRole.SENDER)
                                                         .reportingParty (aIF.createParticipantIdentifierWithDefaultScheme ("0245:2020000001"))
                                                         .receivingParty (aIF.createParticipantIdentifierWithDefaultScheme ("0242:c5id"))
                                                         .reportersRepresentative (aIF.createParticipantIdentifierWithDefaultScheme ("0242:987654"))
                                                         .taxAuthorityID ("XX")
                                                         .reportedTransaction (aInit)
                                                         .build ();
    assertNotNull (aTDD);

    final String sXML = new PeppolSKTDD100Marshaller ().setFormattedOutput (true).getAsString (aTDD);
    assertNotNull (sXML);

    final ValidationResultList aVRL = PeppolSKTDDValidator.validateSK_TDD_100 (new ReadableResourceString (sXML,
                                                                                                           StandardCharsets.UTF_8));
    assertTrue (aVRL.getAllErrors ().getAllMapped (IError::getAsStringLocaleIndepdent).toString (),
                aVRL.getOverallValidity ().isValid ());

    final Document aDoc = DOMReader.readXMLDOM (sXML);
    assertNotNull (aDoc);
    Element e = XMLHelper.getFirstChildElementOfName (aDoc.getDocumentElement (),
                                                      CPeppolSKTDD.TDD_XSD_1_0_0_NS,
                                                      "ReportedTransaction");
    e = XMLHelper.getFirstChildElementOfName (e, CPeppolSKTDD.TDD_XSD_1_0_0_NS, "ReportedDocument");
    e = XMLHelper.getFirstChildElementOfName (e, CUBL21.XML_SCHEMA_CBC_NAMESPACE_URL, "IssueTime");
    assertNotNull (e);
    return e.getTextContent ();
  }

  private static void _assertTakenOverAsGiven (final String sIssueTime)
  {
    final InvoiceType aInvoice = UBL21Marshaller.invoice ().read (_readWithIssueTime (INVOICE_PATH, sIssueTime));
    assertNotNull (aInvoice);
    assertEquals (sIssueTime, _buildAndGetIssueTime (rt -> rt.initFromInvoice (aInvoice)));

    final CreditNoteType aCreditNote = UBL21Marshaller.creditNote ()
                                                      .read (_readWithIssueTime (CREDIT_NOTE_PATH, sIssueTime));
    assertNotNull (aCreditNote);
    assertEquals (sIssueTime, _buildAndGetIssueTime (rt -> rt.initFromCreditNote (aCreditNote)));
  }

  @Test
  public void testWithoutOffset ()
  {
    _assertTakenOverAsGiven (FILE_ISSUE_TIME);
  }

  @Test
  public void testWithOffsetAndMicroseconds ()
  {
    _assertTakenOverAsGiven ("10:15:30.123456+02:00");
  }

  @Test
  public void testUTC ()
  {
    _assertTakenOverAsGiven ("10:15:30Z");
  }

  @Test
  public void testExplicitIssueTime ()
  {
    final InvoiceType aInvoice = UBL21Marshaller.invoice ().read (new ClassPathResource (INVOICE_PATH));
    assertNotNull (aInvoice);

    assertEquals ("11:00:00+01:00",
                  _buildAndGetIssueTime (rt -> rt.initFromInvoice (aInvoice)
                                                 .issueTime (OffsetTime.of (11, 0, 0, 0, ZoneOffset.ofHours (1)))));
  }
}
