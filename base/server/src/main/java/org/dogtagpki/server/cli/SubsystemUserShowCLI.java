//
// Copyright Red Hat, Inc.
//
// SPDX-License-Identifier: GPL-2.0-or-later
//
package org.dogtagpki.server.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.lang3.StringUtils;
import org.dogtagpki.cli.CLI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netscape.certsrv.base.UserNotFoundException;
import com.netscape.certsrv.user.UserData;
import com.netscape.cmscore.apps.EngineConfig;
import com.netscape.cmscore.ldapconn.LDAPConfig;
import com.netscape.cmscore.ldapconn.PKISocketConfig;
import com.netscape.cmscore.usrgrp.UGSubsystem;
import com.netscape.cmscore.usrgrp.UGSubsystemConfig;
import com.netscape.cmscore.usrgrp.User;
import com.netscape.cmsutil.password.IPasswordStore;
import com.netscape.cmsutil.password.PasswordStoreConfig;

/**
 * @author Endi S. Dewata
 */
public class SubsystemUserShowCLI extends SubsystemCLI {

    public static Logger logger = LoggerFactory.getLogger(SubsystemUserShowCLI.class);

    public SubsystemUserShowCLI(CLI parent) {
        super("show", "Display " + parent.getParent().getName().toUpperCase() + " user", parent);
    }

    @Override
    public void createOptions() {

        Option option = new Option(null, "output-format", true, "Output format: text (default), json.");
        option.setArgName("format");
        options.addOption(option);
    }

    @Override
    public void execute(CommandLine cmd) throws Exception {

        String[] cmdArgs = cmd.getArgs();

        if (cmdArgs.length < 1) {
            throw new Exception("Missing user ID");
        }

        String userID = cmdArgs[0];

        String outputFormat = cmd.getOptionValue("output-format", "text");

        initializeTomcatJSS();
        String subsystem = parent.getParent().getName();
        EngineConfig cs = getEngineConfig(subsystem);
        cs.load();

        UGSubsystemConfig ugConfig = cs.getUGSubsystemConfig();
        LDAPConfig ldapConfig = ugConfig.getLDAPConfig();
        ldapConfig.putInteger("minConns", 1);

        PKISocketConfig socketConfig = cs.getSocketConfig();

        PasswordStoreConfig psc = cs.getPasswordStoreConfig();
        IPasswordStore passwordStore = IPasswordStore.create(psc);

        UGSubsystem ugSubsystem = new UGSubsystem();

        UserData userData = new UserData();

        try {
            ugSubsystem.init(ldapConfig, socketConfig, passwordStore);
            User user = ugSubsystem.getUser(userID);

            if (user == null) {
                throw new UserNotFoundException(userID);
            }

            userData.setID(userID);
            userData.setUserID(userID);

            String fullName = user.getFullName();
            if (!StringUtils.isEmpty(fullName)) userData.setFullName(fullName);

            String email = user.getEmail();
            if (!StringUtils.isEmpty(email)) userData.setEmail(email);

            String phone = user.getPhone();
            if (!StringUtils.isEmpty(phone)) userData.setPhone(phone);

            String type = user.getUserType();
            if (!StringUtils.isEmpty(type)) userData.setType(type);

            String state = user.getState();
            if (!StringUtils.isEmpty(state)) userData.setState(state);

        } finally {
            ugSubsystem.shutdown();
        }

        if (outputFormat.equalsIgnoreCase("json")) {
            System.out.println(userData.toJSON());

        } else if (outputFormat.equalsIgnoreCase("text")) {

            System.out.println("  User ID: " + userData.getUserID());

            String fullName = userData.getFullName();
            if (!StringUtils.isEmpty(fullName)) {
                System.out.println("  Full name: " + fullName);
            }

            String email = userData.getEmail();
            if (!StringUtils.isEmpty(email)) {
                System.out.println("  Email: " + email);
            }

            String phone = userData.getPhone();
            if (!StringUtils.isEmpty(phone)) {
                System.out.println("  Phone: " + phone);
            }

            String type = userData.getType();
            if (!StringUtils.isEmpty(type)) {
                System.out.println("  Type: " + type);
            }

            String state = userData.getState();
            if (!StringUtils.isEmpty(state)) {
                System.out.println("  State: " + state);
            }

        } else {
            throw new Exception("Unsupported output format: " + outputFormat);
        }
    }
}
