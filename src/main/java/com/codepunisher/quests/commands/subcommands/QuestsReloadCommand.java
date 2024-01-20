package com.codepunisher.quests.commands.subcommands;

import com.codepunisher.quests.commands.QuestsSubCommand;
import com.codepunisher.quests.commands.lib.CommandCall;

import java.util.function.Consumer;

public class QuestsReloadCommand extends QuestsSubCommand {
    public QuestsReloadCommand(String command, String usage, String permission) {
        super(command, usage, permission);
    }

    @Override
    protected Consumer<CommandCall> getCommandCallConsumer() {
        return call -> {
            call.asPlayer().sendMessage("reload");
        };
    }
}
