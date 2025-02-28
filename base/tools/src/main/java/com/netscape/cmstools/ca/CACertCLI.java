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
// (C) 2012 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---

package com.netscape.cmstools.ca;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.dogtagpki.cli.CLI;

import com.netscape.certsrv.ca.CACertClient;
import com.netscape.certsrv.cert.CertData;
import com.netscape.certsrv.cert.CertDataInfo;
import com.netscape.certsrv.client.PKIClient;
import com.netscape.cmstools.cli.MainCLI;
import com.netscape.cmstools.cli.SubsystemCLI;

/**
 * @author Endi S. Dewata
 */
public class CACertCLI extends CLI {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CACertCLI.class);

    public static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public CACertClient certClient;

    public CACertCLI(CLI parent) {
        super("cert", "Certificate management commands", parent);

        addModule(new CACertFindCLI(this));
        addModule(new CACertShowCLI(this));
        addModule(new CACertExportCLI(this));
        addModule(new CACertRevokeCLI(this));
        addModule(new CACertHoldCLI(this));
        addModule(new CACertReleaseHoldCLI(this));
        addModule(new CACertStatusCLI(this));

        addModule(new CACertRequestCLI(this));

        addModule(new CACertSigningShowCLI(this));
        addModule(new CACertSigningExportCLI(this));
        addModule(new CACertSubsystemShowCLI(this));
        addModule(new CACertSubsystemExportCLI(this));
        addModule(new CACertTransportShowCLI(this));
        addModule(new CACertTransportExportCLI(this));
    }

    @Override
    public String getFullName() {
        if (parent instanceof MainCLI) {
            // do not include MainCLI's name
            return name;
        } else {
            return parent.getFullName() + "-" + name;
        }
    }

    @Override
    public String getManPage() {
        return "pki-cert";
    }

    public CACertClient getCertClient() throws Exception {

        if (certClient != null) return certClient;

        PKIClient client = getClient();

        // determine the subsystem
        String subsystem;
        if (parent instanceof SubsystemCLI) {
            SubsystemCLI subsystemCLI = (SubsystemCLI)parent;
            subsystem = subsystemCLI.getName();
        } else {
            subsystem = "ca";
        }

        // create new cert client
        certClient = new CACertClient(client, subsystem);

        return certClient;
    }

    public static String getAlgorithmNameFromOID(String oid) {
        if (oid == null)
            return "";
        else if (oid.equals("1.2.840.113549.1.1.1"))
            return "PKCS #1 RSA";
        else if (oid.equals("1.2.840.113549.1.1.4"))
            return "PKCS #1 MD5 With RSA";
        else if (oid.equals("1.2.840.10040.4.1"))
            return "DSA";
        else
            return "OID."+oid;
    }

    public static void printCertInfo(CertDataInfo info) {
        System.out.println("  Serial Number: " + info.getID().toHexString());
        System.out.println("  Subject DN: " + info.getSubjectDN());
        System.out.println("  Issuer DN: " + info.getIssuerDN());
        System.out.println("  Status: " + info.getStatus());

        String type = info.getType();
        Integer version = info.getVersion();
        if (version != null) {
            type += " version " + (version + 1);
        }
        System.out.println("  Type: "+type);

        String keyAlgorithm = getAlgorithmNameFromOID(info.getKeyAlgorithmOID());
        Integer keyLength = info.getKeyLength();
        if (keyLength != null) {
            keyAlgorithm += " with " + keyLength + "-bit key";
        }
        System.out.println("  Key Algorithm: "+keyAlgorithm);

        System.out.println("  Not Valid Before: "+info.getNotValidBefore());
        System.out.println("  Not Valid After: "+info.getNotValidAfter());

        System.out.println("  Issued On: "+info.getIssuedOn());
        System.out.println("  Issued By: "+info.getIssuedBy());

        Date revokedOn = info.getRevokedOn();
        if (revokedOn != null) {
            System.out.println("  Revoked On: " + revokedOn);
        }

        String revokedBy = info.getRevokedBy();
        if (revokedBy != null) {
            System.out.println("  Revoked By: " + revokedBy);
        }
    }

    public static void printCertData(
            CertData certData,
            boolean showPrettyPrint,
            boolean showEncoded) {

        System.out.println("  Serial Number: " + certData.getSerialNumber().toHexString());
        System.out.println("  Subject DN: " + certData.getSubjectDN());
        System.out.println("  Issuer DN: " + certData.getIssuerDN());
        System.out.println("  Status: " + certData.getStatus());
        System.out.println("  Not Valid Before: " + certData.getNotBefore());
        System.out.println("  Not Valid After: " + certData.getNotAfter());

        Date revokedOn = certData.getRevokedOn();
        if (revokedOn != null) {
            System.out.println("  Revoked On: " + revokedOn);
        }

        String revokedBy = certData.getRevokedBy();
        if (revokedBy != null) {
            System.out.println("  Revoked By: " + revokedBy);
        }

        String prettyPrint = certData.getPrettyPrint();
        if (showPrettyPrint && prettyPrint != null) {
            System.out.println();
            System.out.println(prettyPrint);
        }

        String encoded = certData.getEncoded();
        if (showEncoded && encoded != null) {
            System.out.println();
            System.out.print(encoded);
        }
    }
}
