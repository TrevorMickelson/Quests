package com.codepunisher.quests.cache;

import com.codepunisher.quests.commands.QuestsSubCommand;
import com.codepunisher.quests.models.CmdType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class QuestSubCommandCache {
    private final Map<CmdType, QuestsSubCommand> questsSubCommandMap = new HashMap<>();

    public void add(CmdType cmdType, QuestsSubCommand questsSubCommand) {
        questsSubCommandMap.put(cmdType, questsSubCommand);
    }

    public Optional<QuestsSubCommand> getQuestSubCommand(CmdType cmdType) {
        return Optional.ofNullable(questsSubCommandMap.get(cmdType));
    }
}
