package ru.homyakin.seeker.telegram.command;

import com.vdurmont.emoji.EmojiParser;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.ChatMemberUpdated;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.homyakin.seeker.infrastructure.TextConstants;
import ru.homyakin.seeker.telegram.command.common.help.SelectHelp;
import ru.homyakin.seeker.telegram.command.group.spin.Spin;
import ru.homyakin.seeker.telegram.command.group.duel.AcceptDuel;
import ru.homyakin.seeker.telegram.command.group.duel.DeclineDuel;
import ru.homyakin.seeker.telegram.command.group.duel.StartDuel;
import ru.homyakin.seeker.telegram.command.group.language.GroupChangeLanguage;
import ru.homyakin.seeker.telegram.command.group.action.JoinGroup;
import ru.homyakin.seeker.telegram.command.group.action.LeftGroup;
import ru.homyakin.seeker.telegram.command.group.language.GroupSelectLanguage;
import ru.homyakin.seeker.telegram.command.group.profile.GetProfileInGroup;
import ru.homyakin.seeker.telegram.command.common.help.ShowHelp;
import ru.homyakin.seeker.telegram.command.group.spin.SpinTop;
import ru.homyakin.seeker.telegram.command.group.stats.GetGroupStats;
import ru.homyakin.seeker.telegram.command.group.tavern_menu.GetTavernMenu;
import ru.homyakin.seeker.telegram.command.group.tavern_menu.Order;
import ru.homyakin.seeker.telegram.command.type.CommandType;
import ru.homyakin.seeker.telegram.command.user.characteristics.CancelResetCharacteristics;
import ru.homyakin.seeker.telegram.command.user.characteristics.IncreaseCharacteristic;
import ru.homyakin.seeker.telegram.command.user.characteristics.ConfirmResetCharacteristics;
import ru.homyakin.seeker.telegram.command.user.navigation.Back;
import ru.homyakin.seeker.telegram.command.user.navigation.ReceptionDesk;
import ru.homyakin.seeker.telegram.command.user.characteristics.LevelUp;
import ru.homyakin.seeker.telegram.command.user.navigation.StartUser;
import ru.homyakin.seeker.telegram.command.user.language.UserChangeLanguage;
import ru.homyakin.seeker.telegram.command.user.language.UserSelectLanguage;
import ru.homyakin.seeker.telegram.command.group.event.JoinEvent;
import ru.homyakin.seeker.telegram.command.user.profile.ChangeName;
import ru.homyakin.seeker.telegram.command.user.profile.GetProfileInPrivate;
import ru.homyakin.seeker.telegram.command.user.characteristics.ResetCharacteristics;
import ru.homyakin.seeker.telegram.utils.TelegramUtils;

@Component
public class CommandParser {
    public Optional<Command> parse(Update update) {
        final Optional<Command> command;
        if (update.hasMyChatMember()) {
            command = parseMyChatMember(update.getMyChatMember());
        } else if (update.hasMessage()) {
            command = parseMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            command = parseCallback(update.getCallbackQuery());
        } else {
            command = Optional.empty();
        }

        return command;
    }

    private Optional<Command> parseMyChatMember(ChatMemberUpdated chatMember) {
        if (TelegramUtils.isGroupChat(chatMember.getChat())) {
            return Optional.ofNullable(
                switch (chatMember.getNewChatMember().getStatus()) {
                    case "left" -> new LeftGroup(chatMember.getChat().getId());
                    case "member" -> new JoinGroup(chatMember.getChat().getId());
                    default -> null;
                }
            );
        } else {
            //TODO обработка сообщений из лички
            return Optional.empty();
        }
    }

    private Optional<Command> parseMessage(Message message) { //TODO разделить на group и private
        if (!message.hasText()) {
            return Optional.empty();
        }
        if (message.isUserMessage()) {
            return parsePrivateMessage(message);
        } else if (TelegramUtils.isGroupMessage(message)) {
            return parseGroupMessage(message);
        } else {
            return Optional.empty();
        }
    }

    private Optional<Command> parsePrivateMessage(Message message) {
        return CommandType.getFromString(
                EmojiParser.parseToAliases(message.getText())
            )
            .map(commandType -> switch (commandType) {
                case CHANGE_LANGUAGE -> UserChangeLanguage.from(message);
                case START -> StartUser.from(message);
                case GET_PROFILE -> GetProfileInPrivate.from(message);
                case SHOW_HELP -> ShowHelp.from(message);
                case CHANGE_NAME -> ChangeName.from(message);
                case LEVEL_UP -> LevelUp.from(message);
                case RECEPTION_DESK -> ReceptionDesk.from(message);
                case BACK -> Back.from(message);
                case RESET_CHARACTERISTICS -> ResetCharacteristics.from(message);
                default -> null;
            });
    }

    private Optional<Command> parseGroupMessage(Message message) {
        return CommandType.getFromString(message.getText().split("@")[0].split(" ")[0])
            .map(commandType -> switch (commandType) {
                case CHANGE_LANGUAGE -> GroupChangeLanguage.from(message);
                case GET_PROFILE -> GetProfileInGroup.from(message);
                case SHOW_HELP -> ShowHelp.from(message);
                case START_DUEL -> StartDuel.from(message);
                case TAVERN_MENU -> GetTavernMenu.from(message);
                case ORDER -> Order.from(message);
                case GROUP_STATS -> GetGroupStats.from(message);
                case SPIN -> Spin.from(message);
                case SPIN_TOP -> SpinTop.from(message);
                default -> null;
            });
    }

    private Optional<Command> parseCallback(CallbackQuery callback) {
        if (callback.getMessage().isUserMessage()) {
            return parsePrivateCallback(callback);
        } else if (TelegramUtils.isGroupMessage(callback.getMessage())) {
            return parseGroupCallback(callback);
        } else {
            return Optional.empty();
        }
    }

    private Optional<Command> parsePrivateCallback(CallbackQuery callback) {
        final var text = callback.getData().split(TextConstants.CALLBACK_DELIMITER)[0];
        return CommandType.getFromString(text)
            .map(commandType -> switch (commandType) {
                case SELECT_LANGUAGE -> UserSelectLanguage.from(callback);
                case SELECT_HELP -> SelectHelp.from(callback);
                case CONFIRM_RESET_CHARACTERISTICS -> ConfirmResetCharacteristics.from(callback);
                case CANCEL_RESET_CHARACTERISTICS -> CancelResetCharacteristics.from(callback);
                case INCREASE_CHARACTERISTIC -> IncreaseCharacteristic.from(callback);
                default -> null;
            });
    }

    private Optional<Command> parseGroupCallback(CallbackQuery callback) {
        final var text = callback.getData().split(TextConstants.CALLBACK_DELIMITER)[0];
        return CommandType.getFromString(text)
            .map(commandType -> switch (commandType) {
                case SELECT_LANGUAGE -> GroupSelectLanguage.from(callback);
                case JOIN_EVENT -> JoinEvent.from(callback);
                case DECLINE_DUEL -> DeclineDuel.from(callback);
                case ACCEPT_DUEL -> AcceptDuel.from(callback);
                case SELECT_HELP -> SelectHelp.from(callback);
                default -> null;
            });
    }

}
