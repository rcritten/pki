//
// Copyright Red Hat, Inc.
//
// SPDX-License-Identifier: GPL-2.0-or-later
//
package org.dogtagpki.server.cli;

import org.dogtagpki.cli.CLI;

/**
 * @author Endi S. Dewata
 */
public class SubsystemDBReplicationCLI extends CLI {

    public SubsystemDBReplicationCLI(CLI parent) {
        super("replication", parent.parent.name.toUpperCase() + " database replication management commands", parent);

        addModule(new SubsystemDBReplicationSetupCLI(this));
    }
}
