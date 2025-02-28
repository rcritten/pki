// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.cms.servlet.ocsp;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dogtagpki.server.authentication.AuthToken;
import org.dogtagpki.server.authorization.AuthzToken;

import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.ocsp.IDefStore;
import com.netscape.cms.servlet.base.CMSServlet;
import com.netscape.cms.servlet.common.CMSRequest;
import com.netscape.cms.servlet.common.CMSTemplate;
import com.netscape.cms.servlet.common.CMSTemplateParams;
import com.netscape.cms.servlet.common.ECMSGWException;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.base.ArgBlock;
import com.netscape.cmscore.dbs.CRLIssuingPointRecord;
import com.netscape.ocsp.OCSPAuthority;

/**
 * Show the list of CA's that the OCSP responder can service
 *
 * @version $Revision$ $Date$
 */
public class ListCAServlet extends CMSServlet {

    /**
     *
     */
    private static final long serialVersionUID = 3764395161795483452L;

    private final static String TPL_FILE = "listCAs.template";
    private String mFormPath = null;
    private OCSPAuthority mOCSPAuthority;

    public ListCAServlet() {
        super();
    }

    /**
     * initialize the servlet. This servlet uses the template file
     * "listCAs.template" to process the response.
     *
     * @param sc servlet configuration, read from the web.xml file
     */
    @Override
    public void init(ServletConfig sc) throws ServletException {
        super.init(sc);
        // override success to display own output.

        mFormPath = "/" + mAuthority.getId() + "/" + TPL_FILE;
        mTemplates.remove(CMSRequest.SUCCESS);
        mOCSPAuthority = (OCSPAuthority) mAuthority;
        if (mOutputTemplatePath != null)
            mFormPath = mOutputTemplatePath;
    }

    /**
     * Process the HTTP request.
     *
     * @param cmsReq the object holding the request and response information
     */
    @Override
    protected void process(CMSRequest cmsReq)
            throws EBaseException {
        HttpServletRequest req = cmsReq.getHttpReq();
        HttpServletResponse resp = cmsReq.getHttpResp();

        AuthToken authToken = authenticate(cmsReq);

        AuthzToken authzToken = null;

        try {
            authzToken = authorize(mAclMethod, authToken,
                        mAuthzResourceName, "list");
        } catch (Exception e) {
            // do nothing for now
        }

        if (authzToken == null) {
            cmsReq.setStatus(CMSRequest.UNAUTHORIZED);
            return;
        }

        CMSTemplate form = null;
        Locale[] locale = new Locale[1];

        try {
            form = getTemplate(mFormPath, req, locale);
        } catch (IOException e) {
            logger.error(CMS.getLogMessage("CMSGW_ERR_GET_TEMPLATE", mFormPath, e.toString()), e);
            throw new ECMSGWException(CMS.getUserMessage("CMS_GW_DISPLAY_TEMPLATE_ERROR"), e);
        }

        ArgBlock header = new ArgBlock();
        ArgBlock fixed = new ArgBlock();
        CMSTemplateParams argSet = new CMSTemplateParams(header, fixed);

        IDefStore defStore = mOCSPAuthority.getDefaultStore();
        Enumeration<CRLIssuingPointRecord> recs = defStore.searchAllCRLIssuingPointRecord(100);

        // show the current CRL number if present
        header.addStringValue("stateCount",
                Integer.toString(defStore.getStateCount()));

        while (recs.hasMoreElements()) {
            CRLIssuingPointRecord rec = recs.nextElement();
            ArgBlock rarg = new ArgBlock();
            String thisId = rec.getId();

            rarg.addStringValue("Id", thisId);
            Date thisUpdate = rec.getThisUpdate();

            if (thisUpdate == null) {
                rarg.addStringValue("ThisUpdate", "UNKNOWN");
            } else {
                rarg.addStringValue("ThisUpdate", thisUpdate.toString());
            }
            Date nextUpdate = rec.getNextUpdate();

            if (nextUpdate == null) {
                rarg.addStringValue("NextUpdate", "UNKNOWN");
            } else {
                rarg.addStringValue("NextUpdate", nextUpdate.toString());
            }
            Long rc = rec.getCRLSize();

            if (rc == null) {
                rarg.addLongValue("NumRevoked", 0);
            } else {
                if (rc.longValue() == -1) {
                    rarg.addStringValue("NumRevoked", "UNKNOWN");
                } else {
                    rarg.addLongValue("NumRevoked", rc.longValue());
                }
            }

            BigInteger crlNumber = rec.getCRLNumber();
            if (crlNumber == null || crlNumber.equals(new BigInteger("-1"))) {
                rarg.addStringValue("CRLNumber", "UNKNOWN");
            } else {
                rarg.addStringValue("CRLNumber", crlNumber.toString());
            }

            rarg.addLongValue("ReqCount", defStore.getReqCount(thisId));
            argSet.addRepeatRecord(rarg);
        }

        try {
            ServletOutputStream out = resp.getOutputStream();

            String xmlOutput = req.getParameter("xml");
            if (xmlOutput != null && xmlOutput.equals("true")) {
                outputXML(resp, argSet);
            } else {
                resp.setContentType("text/html");
                form.renderOutput(out, argSet);
                cmsReq.setStatus(CMSRequest.SUCCESS);
            }
        } catch (IOException e) {
            logger.error(CMS.getLogMessage("CMSGW_ERR_STREAM_TEMPLATE", e.toString()), e);
            throw new ECMSGWException(CMS.getUserMessage("CMS_GW_DISPLAY_TEMPLATE_ERROR"), e);
        }
    }
}
