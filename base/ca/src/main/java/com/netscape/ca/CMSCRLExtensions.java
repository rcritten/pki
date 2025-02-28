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
package com.netscape.ca;

import java.security.cert.CertificateException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

import org.dogtagpki.server.ca.CAConfig;
import org.dogtagpki.server.ca.CAEngine;
import org.dogtagpki.server.ca.CAEngineConfig;
import org.dogtagpki.server.ca.ICMSCRLExtension;
import org.dogtagpki.server.ca.ICMSCRLExtensions;
import org.mozilla.jss.netscape.security.extensions.AuthInfoAccessExtension;
import org.mozilla.jss.netscape.security.x509.AuthorityKeyIdentifierExtension;
import org.mozilla.jss.netscape.security.x509.CRLExtensions;
import org.mozilla.jss.netscape.security.x509.CRLNumberExtension;
import org.mozilla.jss.netscape.security.x509.CRLReasonExtension;
import org.mozilla.jss.netscape.security.x509.DeltaCRLIndicatorExtension;
import org.mozilla.jss.netscape.security.x509.Extension;
import org.mozilla.jss.netscape.security.x509.FreshestCRLExtension;
import org.mozilla.jss.netscape.security.x509.HoldInstructionExtension;
import org.mozilla.jss.netscape.security.x509.InvalidityDateExtension;
import org.mozilla.jss.netscape.security.x509.IssuerAlternativeNameExtension;
import org.mozilla.jss.netscape.security.x509.IssuingDistributionPointExtension;
import org.mozilla.jss.netscape.security.x509.OIDMap;
import org.mozilla.jss.netscape.security.x509.PKIXExtensions;

import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.EPropertyNotDefined;
import com.netscape.certsrv.base.EPropertyNotFound;
import com.netscape.certsrv.common.Constants;
import com.netscape.certsrv.common.NameValuePairs;
import com.netscape.cms.crl.CMSIssuingDistributionPointExtension;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.base.ConfigStore;

public class CMSCRLExtensions implements ICMSCRLExtensions {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CMSCRLExtensions.class);

    public static final String PROP_ENABLE = "enable";
    public static final String PROP_CLASS = "class";
    public static final String PROP_TYPE = "type";
    public static final String PROP_CRITICAL = "critical";
    public static final String PROP_CRL_EXT = "CRLExtension";
    public static final String PROP_CRL_ENTRY_EXT = "CRLEntryExtension";

    private CRLIssuingPoint mCRLIssuingPoint;

    @SuppressWarnings("unused")
    private CRLIssuingPointConfig mConfig;
    private CRLIssuingPointExtensionsConfig mCRLExtConfig;

    private Vector<String> mCRLExtensionNames = new Vector<>();
    private Vector<String> mCRLEntryExtensionNames = new Vector<>();
    private Vector<String> mEnabledCRLExtensions = new Vector<>();
    private Vector<String> mCriticalCRLExtensions = new Vector<>();
    private Hashtable<String, String> mCRLExtensionClassNames = new Hashtable<>();
    private Hashtable<String, String> mCRLExtensionIDs = new Hashtable<>();

    private static final Vector<String> mDefaultCRLExtensionNames = new Vector<>();
    private static final Vector<String> mDefaultCRLEntryExtensionNames = new Vector<>();
    private static final Vector<String> mDefaultEnabledCRLExtensions = new Vector<>();
    private static final Vector<String> mDefaultCriticalCRLExtensions = new Vector<>();
    private static final Hashtable<String, String> mDefaultCRLExtensionClassNames = new Hashtable<>();
    private static final Hashtable<String, String> mDefaultCRLExtensionIDs = new Hashtable<>();

    static {

        /* Default CRL Extensions */
        mDefaultCRLExtensionNames.addElement(AuthorityKeyIdentifierExtension.NAME);
        mDefaultCRLExtensionNames.addElement(IssuerAlternativeNameExtension.NAME);
        mDefaultCRLExtensionNames.addElement(CRLNumberExtension.NAME);
        mDefaultCRLExtensionNames.addElement(DeltaCRLIndicatorExtension.NAME);
        mDefaultCRLExtensionNames.addElement(IssuingDistributionPointExtension.NAME);
        mDefaultCRLExtensionNames.addElement(FreshestCRLExtension.NAME);
        mDefaultCRLExtensionNames.addElement(AuthInfoAccessExtension.NAME2);

        /* Default CRL Entry Extensions */
        mDefaultCRLEntryExtensionNames.addElement(CRLReasonExtension.NAME);
        //mDefaultCRLEntryExtensionNames.addElement(HoldInstructionExtension.NAME);
        mDefaultCRLEntryExtensionNames.addElement(InvalidityDateExtension.NAME);
        //mDefaultCRLEntryExtensionNames.addElement(CertificateIssuerExtension.NAME);

        /* Default Enabled CRL Extensions */
        mDefaultEnabledCRLExtensions.addElement(CRLNumberExtension.NAME);
        //mDefaultEnabledCRLExtensions.addElement(DeltaCRLIndicatorExtension.NAME);
        mDefaultEnabledCRLExtensions.addElement(CRLReasonExtension.NAME);
        mDefaultEnabledCRLExtensions.addElement(InvalidityDateExtension.NAME);

        /* Default Critical CRL Extensions */
        mDefaultCriticalCRLExtensions.addElement(DeltaCRLIndicatorExtension.NAME);
        mDefaultCriticalCRLExtensions.addElement(IssuingDistributionPointExtension.NAME);
        //mDefaultCriticalCRLExtensions.addElement(CertificateIssuerExtension.NAME);

        /* CRL extension IDs */
        mDefaultCRLExtensionIDs.put(PKIXExtensions.AuthorityKey_Id.toString(),
                AuthorityKeyIdentifierExtension.NAME);
        mDefaultCRLExtensionIDs.put(PKIXExtensions.IssuerAlternativeName_Id.toString(),
                IssuerAlternativeNameExtension.NAME);
        mDefaultCRLExtensionIDs.put(PKIXExtensions.CRLNumber_Id.toString(),
                CRLNumberExtension.NAME);
        mDefaultCRLExtensionIDs.put(PKIXExtensions.DeltaCRLIndicator_Id.toString(),
                DeltaCRLIndicatorExtension.NAME);
        mDefaultCRLExtensionIDs.put(PKIXExtensions.IssuingDistributionPoint_Id.toString(),
                IssuingDistributionPointExtension.NAME);
        mDefaultCRLExtensionIDs.put(PKIXExtensions.ReasonCode_Id.toString(),
                CRLReasonExtension.NAME);
        mDefaultCRLExtensionIDs.put(PKIXExtensions.HoldInstructionCode_Id.toString(),
                HoldInstructionExtension.NAME);
        mDefaultCRLExtensionIDs.put(PKIXExtensions.InvalidityDate_Id.toString(),
                InvalidityDateExtension.NAME);
        //mDefaultCRLExtensionIDs.put(PKIXExtensions.CertificateIssuer_Id.toString(),
        //                     CertificateIssuerExtension.NAME);
        mDefaultCRLExtensionIDs.put(PKIXExtensions.FreshestCRL_Id.toString(),
                FreshestCRLExtension.NAME);
        mDefaultCRLExtensionIDs.put(AuthInfoAccessExtension.ID.toString(),
                AuthInfoAccessExtension.NAME2);

        /* Class names */
        mDefaultCRLExtensionClassNames.put(AuthorityKeyIdentifierExtension.NAME,
                "com.netscape.cms.crl.CMSAuthorityKeyIdentifierExtension");
        mDefaultCRLExtensionClassNames.put(IssuerAlternativeNameExtension.NAME,
                "com.netscape.cms.crl.CMSIssuerAlternativeNameExtension");
        mDefaultCRLExtensionClassNames.put(CRLNumberExtension.NAME,
                "com.netscape.cms.crl.CMSCRLNumberExtension");
        mDefaultCRLExtensionClassNames.put(DeltaCRLIndicatorExtension.NAME,
                "com.netscape.cms.crl.CMSDeltaCRLIndicatorExtension");
        mDefaultCRLExtensionClassNames.put(IssuingDistributionPointExtension.NAME,
                "com.netscape.cms.crl.CMSIssuingDistributionPointExtension");
        mDefaultCRLExtensionClassNames.put(CRLReasonExtension.NAME,
                "com.netscape.cms.crl.CMSCRLReasonExtension");
        mDefaultCRLExtensionClassNames.put(HoldInstructionExtension.NAME,
                "com.netscape.cms.crl.CMSHoldInstructionExtension");
        mDefaultCRLExtensionClassNames.put(InvalidityDateExtension.NAME,
                "com.netscape.cms.crl.CMSInvalidityDateExtension");
        //mDefaultCRLExtensionClassNames.put(CertificateIssuerExtension.NAME,
        //        "com.netscape.cms.crl.CMSCertificateIssuerExtension");
        mDefaultCRLExtensionClassNames.put(FreshestCRLExtension.NAME,
                "com.netscape.cms.crl.CMSFreshestCRLExtension");
        mDefaultCRLExtensionClassNames.put(AuthInfoAccessExtension.NAME2,
                "com.netscape.cms.crl.CMSAuthInfoAccessExtension");

        try {
            OIDMap.addAttribute(DeltaCRLIndicatorExtension.class.getName(),
                    DeltaCRLIndicatorExtension.OID,
                    DeltaCRLIndicatorExtension.NAME);
        } catch (CertificateException e) {
        }
        try {
            OIDMap.addAttribute(HoldInstructionExtension.class.getName(),
                    HoldInstructionExtension.OID,
                    HoldInstructionExtension.NAME);
        } catch (CertificateException e) {
        }
        try {
            OIDMap.addAttribute(InvalidityDateExtension.class.getName(),
                    InvalidityDateExtension.OID,
                    InvalidityDateExtension.NAME);
        } catch (CertificateException e) {
        }
        try {
            OIDMap.addAttribute(FreshestCRLExtension.class.getName(),
                    FreshestCRLExtension.OID,
                    FreshestCRLExtension.NAME);
        } catch (CertificateException e) {
        }
    }

    /**
     * Constructs a CRL extensions for CRL issuing point.
     */
    public CMSCRLExtensions(CRLIssuingPoint crlIssuingPoint, CRLIssuingPointConfig config) {
        boolean modifiedConfig = false;

        CAEngine engine = CAEngine.getInstance();
        mConfig = config;
        mCRLExtConfig = config.getExtensionsConfig();
        mCRLIssuingPoint = crlIssuingPoint;

        CAEngineConfig mFileConfig = engine.getConfig();

        ConfigStore crlExtConfig = mFileConfig;
        StringTokenizer st = new StringTokenizer(mCRLExtConfig.getName(), ".");

        while (st.hasMoreTokens()) {
            String subStoreName = st.nextToken();
            ConfigStore newConfig = crlExtConfig.getSubStore(subStoreName, ConfigStore.class);

            if (newConfig != null) {
                crlExtConfig = newConfig;
            }
        }

        if (crlExtConfig != null) {
            Enumeration<String> enumExts = crlExtConfig.getSubStoreNames();

            while (enumExts.hasMoreElements()) {
                String extName = enumExts.nextElement();
                ConfigStore extConfig = crlExtConfig.getSubStore(extName, ConfigStore.class);

                if (extConfig != null) {
                    modifiedConfig |= getEnableProperty(extName, extConfig);
                    modifiedConfig |= getCriticalProperty(extName, extConfig);
                    modifiedConfig |= getTypeProperty(extName, extConfig);
                    modifiedConfig |= getClassProperty(extName, extConfig);
                }
            }

            if (modifiedConfig) {
                try {
                    mFileConfig.commit(true);
                } catch (EBaseException e) {
                    logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_SAVE_CONF", e.toString()), e);
                }
            }
        }
    }

    private boolean getEnableProperty(String extName, ConfigStore extConfig) {
        boolean modifiedConfig = false;

        try {
            if (extConfig.getBoolean(PROP_ENABLE)) {
                mEnabledCRLExtensions.addElement(extName);
            }

        } catch (EPropertyNotFound e) {
            extConfig.putBoolean(PROP_ENABLE, mDefaultEnabledCRLExtensions.contains(extName));
            modifiedConfig = true;
            if (mDefaultEnabledCRLExtensions.contains(extName)) {
                mEnabledCRLExtensions.addElement(extName);
            }
            logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_NO_ENABLE", extName,
                            mDefaultEnabledCRLExtensions.contains(extName) ? "true" : "false"), e);

        } catch (EPropertyNotDefined e) {
            extConfig.putBoolean(PROP_ENABLE, mDefaultEnabledCRLExtensions.contains(extName));
            modifiedConfig = true;
            if (mDefaultEnabledCRLExtensions.contains(extName)) {
                mEnabledCRLExtensions.addElement(extName);
            }
            logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_UNDEFINE_ENABLE", extName,
                            mDefaultEnabledCRLExtensions.contains(extName) ? "true" : "false"), e);

        } catch (EBaseException e) {
            extConfig.putBoolean(PROP_ENABLE, mDefaultEnabledCRLExtensions.contains(extName));
            modifiedConfig = true;
            if (mDefaultEnabledCRLExtensions.contains(extName)) {
                mEnabledCRLExtensions.addElement(extName);
            }
            logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_INVALID_ENABLE", extName,
                            mDefaultEnabledCRLExtensions.contains(extName) ? "true" : "false"), e);
        }

        return modifiedConfig;
    }

    private boolean getCriticalProperty(String extName, ConfigStore extConfig) {
        boolean modifiedConfig = false;

        try {
            if (extConfig.getBoolean(PROP_CRITICAL)) {
                mCriticalCRLExtensions.addElement(extName);
            }

        } catch (EPropertyNotFound e) {
            extConfig.putBoolean(PROP_CRITICAL, mDefaultCriticalCRLExtensions.contains(extName));
            modifiedConfig = true;
            if (mDefaultCriticalCRLExtensions.contains(extName)) {
                mCriticalCRLExtensions.addElement(extName);
            }
            logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_NO_CRITICAL", extName,
                            mDefaultEnabledCRLExtensions.contains(extName) ? "true" : "false"), e);

        } catch (EPropertyNotDefined e) {
            extConfig.putBoolean(PROP_CRITICAL, mDefaultCriticalCRLExtensions.contains(extName));
            modifiedConfig = true;
            if (mDefaultCriticalCRLExtensions.contains(extName)) {
                mCriticalCRLExtensions.addElement(extName);
            }
            logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_UNDEFINE_CRITICAL", extName,
                            mDefaultEnabledCRLExtensions.contains(extName) ? "true" : "false"), e);

        } catch (EBaseException e) {
            extConfig.putBoolean(PROP_CRITICAL, mDefaultCriticalCRLExtensions.contains(extName));
            modifiedConfig = true;
            if (mDefaultCriticalCRLExtensions.contains(extName)) {
                mCriticalCRLExtensions.addElement(extName);
            }
            logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_INVALID_CRITICAL", extName,
                            mDefaultEnabledCRLExtensions.contains(extName) ? "true" : "false"), e);
        }

        return modifiedConfig;
    }

    private boolean getTypeProperty(String extName, ConfigStore extConfig) {
        boolean modifiedConfig = false;
        String extType = null;

        try {
            extType = extConfig.getString(PROP_TYPE);
            if (extType.length() > 0) {
                if (extType.equals(PROP_CRL_ENTRY_EXT)) {
                    mCRLEntryExtensionNames.addElement(extName);
                } else if (extType.equals(PROP_CRL_EXT)) {
                    mCRLExtensionNames.addElement(extName);
                } else {
                    if (mDefaultCRLEntryExtensionNames.contains(extName)) {
                        extConfig.putString(PROP_TYPE, PROP_CRL_ENTRY_EXT);
                        modifiedConfig = true;
                        mCRLEntryExtensionNames.addElement(extName);
                        logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_INVALID_EXT", extName, PROP_CRL_ENTRY_EXT));

                    } else if (mDefaultCRLExtensionNames.contains(extName)) {
                        extConfig.putString(PROP_TYPE, PROP_CRL_EXT);
                        modifiedConfig = true;
                        mCRLExtensionNames.addElement(extName);
                        logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_INVALID_EXT", extName, PROP_CRL_EXT));

                    } else {
                        logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_INVALID_EXT", extName, ""));
                    }
                }

            } else {
                logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_UNDEFINE_EXT", extName));
            }

        } catch (EPropertyNotFound e) {
            if (mDefaultCRLEntryExtensionNames.contains(extName)) {
                extConfig.putString(PROP_TYPE, PROP_CRL_ENTRY_EXT);
                modifiedConfig = true;
            } else if (mDefaultCRLExtensionNames.contains(extName)) {
                extConfig.putString(PROP_TYPE, PROP_CRL_EXT);
                modifiedConfig = true;
            }
            logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_MISSING_EXT", extName), e);

        } catch (EBaseException e) {
            logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_INVALID_EXT", extName, ""), e);
        }

        return modifiedConfig;
    }

    private boolean getClassProperty(String extName, ConfigStore extConfig) {
        boolean modifiedConfig = false;
        String extClass = null;

        try {
            extClass = extConfig.getString(PROP_CLASS);
            if (extClass.length() > 0) {
                mCRLExtensionClassNames.put(extName, extClass);

                try {
                    @SuppressWarnings("unchecked")
                    Class<ICMSCRLExtension> crlExtClass = (Class<ICMSCRLExtension>) Class.forName(extClass);

                    if (crlExtClass != null) {
                        ICMSCRLExtension cmsCRLExt = crlExtClass.getDeclaredConstructor().newInstance();

                        if (cmsCRLExt != null) {
                            String id = cmsCRLExt.getCRLExtOID();

                            if (id != null) {
                                mCRLExtensionIDs.put(id, extName);
                            }
                        }
                    }

                } catch (Exception e) {
                    logger.warn("CMSCRLExtensions: " + e.getMessage(), e);
                }

            } else {
                if (mDefaultCRLExtensionClassNames.containsKey(extName)) {
                    extClass = mCRLExtensionClassNames.get(extName);
                    extConfig.putString(PROP_CLASS, extClass);
                    modifiedConfig = true;
                }
                logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_CLASS_NOT_DEFINED", extName));
            }

        } catch (EPropertyNotFound e) {
            if (mDefaultCRLExtensionClassNames.containsKey(extName)) {
                extClass = mDefaultCRLExtensionClassNames.get(extName);
                extConfig.putString(PROP_CLASS, extClass);
                modifiedConfig = true;
            }
            logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_CLASS_MISSING", extName), e);

        } catch (EBaseException e) {
            if (mDefaultCRLExtensionClassNames.containsKey(extName)) {
                extClass = mDefaultCRLExtensionClassNames.get(extName);
                extConfig.putString(PROP_CLASS, extClass);
                modifiedConfig = true;
            }
            logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_CLASS_INVALID", extName), e);
        }

        return modifiedConfig;
    }

    public boolean isCRLExtension(String extName) {
        return mCRLExtensionNames.contains(extName);
    }

    public boolean isCRLEntryExtension(String extName) {
        return mCRLEntryExtensionNames.contains(extName);
    }

    public boolean isCRLExtensionEnabled(String extName) {
        return ((mCRLExtensionNames.contains(extName) || mCRLEntryExtensionNames.contains(extName)) &&
                mEnabledCRLExtensions.contains(extName));
    }

    public boolean isCRLExtensionCritical(String extName) {
        return mCriticalCRLExtensions.contains(extName);
    }

    public String getCRLExtensionName(String id) {
        String name = null;

        if (mCRLExtensionIDs.containsKey(id)) {
            name = mCRLExtensionIDs.get(id);
        }
        return name;
    }

    public Vector<String> getCRLExtensionNames() {
        return new Vector<>(mCRLExtensionNames);
    }

    public Vector<String> getCRLEntryExtensionNames() {
        return new Vector<>(mCRLEntryExtensionNames);
    }

    public void addToCRLExtensions(CRLExtensions crlExts, String extName, Extension ext) {
        if (mCRLExtensionClassNames.containsKey(extName)) {
            String name = mCRLExtensionClassNames.get(extName);

            try {
                @SuppressWarnings("unchecked")
                Class<ICMSCRLExtension> extClass = (Class<ICMSCRLExtension>) Class.forName(name);

                if (extClass != null) {
                    ICMSCRLExtension cmsCRLExt = extClass.getDeclaredConstructor().newInstance();

                    if (cmsCRLExt != null) {
                        if (ext != null) {
                            if (isCRLExtensionCritical(extName) ^ ext.isCritical()) {
                                ext = cmsCRLExt.setCRLExtensionCriticality(
                                            ext, isCRLExtensionCritical(extName));
                            }
                        } else {
                            ext = cmsCRLExt.getCRLExtension(
                                    mCRLExtConfig.getSubStore(extName, ConfigStore.class),
                                    mCRLIssuingPoint,
                                    isCRLExtensionCritical(extName));
                        }

                        if (crlExts != null && ext != null) {
                            crlExts.set(extName, ext);
                        }
                    }
                }

            } catch (Exception e) {
                logger.warn("CMSCRLExtensions: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public NameValuePairs getConfigParams(String id) {
        NameValuePairs nvp = null;

        if (mCRLEntryExtensionNames.contains(id) ||
                mCRLExtensionNames.contains(id)) {
            nvp = new NameValuePairs();

            /*
             if (mCRLEntryExtensionNames.contains(id)) {
             nvp.add(Constants.PR_CRLEXT_IMPL_NAME, "CRLEntryExtension");
             } else {
             nvp.add(Constants.PR_CRLEXT_IMPL_NAME, "CRLExtension");
             }

             if (mCRLEntryExtensionNames.contains(id)) {
             nvp.add(PROP_TYPE, "CRLEntryExtension");
             } else {
             nvp.add(PROP_TYPE, "CRLExtension");
             }
             */

            if (mEnabledCRLExtensions.contains(id)) {
                nvp.put(PROP_ENABLE, Constants.TRUE);
            } else {
                nvp.put(PROP_ENABLE, Constants.FALSE);
            }
            if (mCriticalCRLExtensions.contains(id)) {
                nvp.put(PROP_CRITICAL, Constants.TRUE);
            } else {
                nvp.put(PROP_CRITICAL, Constants.FALSE);
            }

            if (mCRLExtensionClassNames.containsKey(id)) {
                String name = mCRLExtensionClassNames.get(id);

                if (name != null) {

                    try {
                        Class<?> extClass = Class.forName(name);

                        if (extClass != null) {
                            ICMSCRLExtension cmsCRLExt = (ICMSCRLExtension) extClass.getDeclaredConstructor().newInstance();

                            if (cmsCRLExt != null) {
                                cmsCRLExt.getConfigParams(
                                        mCRLExtConfig.getSubStore(id, ConfigStore.class),
                                        nvp);
                            }
                        }

                    } catch (Exception e) {
                        logger.warn("CMSCRLExtensions: " + e.getMessage(), e);
                    }

                    int i = name.lastIndexOf('.');

                    if ((i > -1) && (i + 1 < name.length())) {
                        String idName = name.substring(i + 1);

                        if (idName != null) {
                            nvp.put(Constants.PR_CRLEXT_IMPL_NAME, idName);
                        }
                    }
                }
            }
        }
        return nvp;
    }

    @Override
    public void setConfigParams(String id, NameValuePairs nvp, ConfigStore config) {

        CAEngine engine = CAEngine.getInstance();
        CertificateAuthority ca = engine.getCA();

        String ipId = nvp.get("id");

        CRLIssuingPoint ip = null;
        if (ipId != null && ca != null) {
            ip = engine.getCRLIssuingPoint(ipId);
        }

        for (Map.Entry<String,String> entry : nvp.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();

            if (name.equals(PROP_ENABLE)) {
                if (!(value.equals(Constants.TRUE) || value.equals(Constants.FALSE))) {
                    continue;
                }
                if (value.equals(Constants.TRUE)) {
                    if (!(mEnabledCRLExtensions.contains(id))) {
                        mEnabledCRLExtensions.addElement(id);
                    }
                }
                if (value.equals(Constants.FALSE)) {
                    mEnabledCRLExtensions.remove(id);
                }
            }

            if (name.equals(PROP_CRITICAL)) {
                if (!(value.equals(Constants.TRUE) || value.equals(Constants.FALSE))) {
                    continue;
                }
                if (value.equals(Constants.TRUE)) {
                    if (!(mCriticalCRLExtensions.contains(id))) {
                        mCriticalCRLExtensions.addElement(id);
                    }
                }
                if (value.equals(Constants.FALSE)) {
                    mCriticalCRLExtensions.remove(id);
                }
            }
            //Sync the onlyContainsCACerts with similar property in CRLIssuingPoint
            //called caCertsOnly.
            if (name.equals(CMSIssuingDistributionPointExtension.PROP_CACERTS)) {
                NameValuePairs crlIssuingPointPairs = null;
                boolean crlCACertsOnly = false;

                boolean issuingDistPointExtEnabled = false;
                CMSCRLExtensions cmsCRLExtensions = null;
                if (ip != null) {
                    cmsCRLExtensions = (CMSCRLExtensions) ip.getCRLExtensions();
                }
                if (cmsCRLExtensions != null) {
                    issuingDistPointExtEnabled =
                            cmsCRLExtensions.isCRLExtensionEnabled(IssuingDistributionPointExtension.NAME);
                }

                logger.debug("issuingDistPointExtEnabled = " + issuingDistPointExtEnabled);

                if (!(value.equals(Constants.TRUE) || value.equals(Constants.FALSE))) {
                    continue;
                }

                //Get value of caCertsOnly from CRLIssuingPoint
                if ((ip != null) && (issuingDistPointExtEnabled == true)) {
                    crlCACertsOnly = ip.isCACertsOnly();
                    logger.debug("CRLCACertsOnly is: " + crlCACertsOnly);
                    crlIssuingPointPairs = new NameValuePairs();

                }

                String newValue = "";
                boolean modifiedCRLConfig = false;
                //If the CRLCACertsOnly prop is false change it to true to sync.
                if (value.equals(Constants.TRUE) && (issuingDistPointExtEnabled == true)) {
                    if (crlCACertsOnly == false) {
                        logger.debug(" value = true and CRLCACertsOnly is already false.");
                        crlIssuingPointPairs.put(Constants.PR_CA_CERTS_ONLY, Constants.TRUE);
                        newValue = Constants.TRUE;
                        ip.updateConfig(crlIssuingPointPairs);
                        modifiedCRLConfig = true;
                    }
                }

                //If the CRLCACertsOnly prop is true change it to false to sync.
                if (value.equals(Constants.FALSE) && (issuingDistPointExtEnabled == true)) {
                    if (ip != null) {
                        crlIssuingPointPairs.put(Constants.PR_CA_CERTS_ONLY, Constants.FALSE);
                        ip.updateConfig(crlIssuingPointPairs);
                        newValue = Constants.FALSE;
                        modifiedCRLConfig = true;
                    }
                }

                if (modifiedCRLConfig == true) {
                    //Commit to this CRL IssuingPoint's config store
                    CAConfig caConfig = ca.getConfigStore();
                    CRLConfig crlConfig = caConfig.getCRLConfig();
                    CRLIssuingPointConfig ipConfig = crlConfig.getCRLIssuingPointConfig(ipId);

                    try {
                        ipConfig.putString(Constants.PR_CA_CERTS_ONLY, newValue);
                        ipConfig.commit(true);

                    } catch (EBaseException e) {
                        logger.warn(CMS.getLogMessage("CMSCORE_CA_CRLEXTS_SAVE_CONF", e.toString()), e);
                    }
                }
            }

            config.putString(name, value);
        }
    }

    @Override
    public String getClassPath(String name) {
        Enumeration<String> enum1 = mCRLExtensionClassNames.elements();

        while (enum1.hasMoreElements()) {
            String extClassName = enum1.nextElement();

            if (extClassName != null) {
                int i = extClassName.lastIndexOf('.');

                if ((i > -1) && (i + 1 < extClassName.length())) {
                    String idName = extClassName.substring(i + 1);

                    if (idName != null) {
                        if (name.equals(idName)) {
                            return extClassName;
                        }
                    }
                }
            }
        }

        return null;
    }
}
