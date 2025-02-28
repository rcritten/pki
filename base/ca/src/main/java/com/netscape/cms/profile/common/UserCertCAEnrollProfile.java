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
package com.netscape.cms.profile.common;

import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.common.NameValuePairs;
import com.netscape.cms.profile.def.PolicyDefault;
import com.netscape.cmscore.base.ConfigStore;

/**
 * This class implements a Certificate Manager enrollment
 * profile for User Certificates.
 *
 * @version $Revision$, $Date$
 */
public class UserCertCAEnrollProfile extends CAEnrollProfile {

    /**
     * Called after initialization. It populates default
     * policies, inputs, and outputs.
     */
    @Override
    public void populate() throws EBaseException {
        // create inputs
        NameValuePairs inputParams1 = new NameValuePairs();
        createProfileInput("i1", "keyGenInputImpl", inputParams1);
        NameValuePairs inputParams2 = new NameValuePairs();
        createProfileInput("i2", "subjectNameInputImpl", inputParams2);
        createProfileInput("i3", "submitterInfoInputImpl", inputParams2);

        // create outputs
        NameValuePairs outputParams1 = new NameValuePairs();
        createProfileOutput("o1", "certOutputImpl", outputParams1);

        // create policies
        createProfilePolicy("set1", "p1",
                        "userSubjectNameDefaultImpl", "noConstraintImpl");

        ProfilePolicy policy2 =
                createProfilePolicy("set1", "p2",
                        "validityDefaultImpl", "noConstraintImpl");
        PolicyDefault def2 = policy2.getDefault();
        ConfigStore defConfig2 = def2.getConfigStore();
        defConfig2.putString("params.range", "180");
        defConfig2.putString("params.startTime", "0");

        ProfilePolicy policy3 =
                createProfilePolicy("set1", "p3",
                        "userKeyDefaultImpl", "noConstraintImpl");
        PolicyDefault def3 = policy3.getDefault();
        ConfigStore defConfig3 = def3.getConfigStore();
        defConfig3.putString("params.keyType", "RSA");
        defConfig3.putString("params.keyMinLength", "512");
        defConfig3.putString("params.keyMaxLength", "4096");

        ProfilePolicy policy4 =
                createProfilePolicy("set1", "p4",
                        "signingAlgDefaultImpl", "noConstraintImpl");
        PolicyDefault def4 = policy4.getDefault();
        ConfigStore defConfig4 = def4.getConfigStore();
        defConfig4.putString("params.signingAlg", "-");
        defConfig4
                .putString(
                        "params.signingAlgsAllowed",
                        "SHA256withRSA,SHA384withRSA,SHA512withRSA,SHA256withEC,SHA384withEC,SHA512withEC");

        ProfilePolicy policy5 =
                createProfilePolicy("set1", "p5",
                        "keyUsageExtDefaultImpl", "noConstraintImpl");
        PolicyDefault def5 = policy5.getDefault();
        ConfigStore defConfig5 = def5.getConfigStore();
        defConfig5.putString("params.keyUsageCritical", "true");
        defConfig5.putString("params.keyUsageCrlSign", "false");
        defConfig5.putString("params.keyUsageDataEncipherment", "false");
        defConfig5.putString("params.keyUsageDecipherOnly", "false");
        defConfig5.putString("params.keyUsageDigitalSignature", "true");
        defConfig5.putString("params.keyUsageEncipherOnly", "false");
        defConfig5.putString("params.keyUsageKeyAgreement", "false");
        defConfig5.putString("params.keyUsageKeyCertSign", "false");
        defConfig5.putString("params.keyUsageKeyEncipherment", "true");
        defConfig5.putString("params.keyUsageNonRepudiation", "true");
    }
}
