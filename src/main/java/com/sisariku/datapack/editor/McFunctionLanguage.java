package com.sisariku.datapack.editor;

import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.*;
import net.minecraft.text.Style;

import java.util.*;

/** mcfunction 语法高亮 — IDEA Darcula 配色 */
public class McFunctionLanguage {

    public static final TokenType SELECTOR = new TokenType("selector").setPattern("@[a-z]");
    public static final TokenType COMMAND  = new TokenType("command").setPattern("[a-zA-Z][a-zA-Z0-9_]*");
    public static final TokenType BRACKET  = new TokenType("bracket").setPattern("\\[[^\\]]*\\]");
    public static final TokenType NBT      = new TokenType("nbt").setPattern("\\{[^}]*\\}");

    public static final StyleManager STYLE_MANAGER = new StyleManager();
    public static final ILanguageDefinition LANGUAGE;

    static {
        List<TokenType> tokens = new ArrayList<>();
        tokens.add(TokenTypes.COMMENT);
        tokens.add(SELECTOR);
        tokens.add(TokenTypes.STRING);
        tokens.add(TokenTypes.NUMBER);
        tokens.add(COMMAND);
        tokens.add(BRACKET);
        tokens.add(NBT);
        tokens.add(TokenTypes.WHITESPACE);
        tokens.add(TokenTypes.OTHER);
        LANGUAGE = new LanguageDefinition("mcfunction", tokens, new HashSet<>());

        // IDEA Darcula 配色
        STYLE_MANAGER.getStyleMap().putAll(Map.of(
            TokenTypes.COMMENT.name, Style.EMPTY.withColor(0x808080),  // 灰色
            SELECTOR.name,           Style.EMPTY.withColor(0xCF8EFF),  // 紫色 @a @p
            TokenTypes.STRING.name,  Style.EMPTY.withColor(0x6AAB73),  // 绿色 "text"
            TokenTypes.NUMBER.name,  Style.EMPTY.withColor(0x2AACB8),  // 青色 123
            COMMAND.name,            Style.EMPTY.withColor(0xFFC66D),  // 金色 execute
            BRACKET.name,            Style.EMPTY.withColor(0xAAAAAA),  // 浅灰 [...]
            NBT.name,                Style.EMPTY.withColor(0xCC7832)   // 橙色 {nbt}
        ));
    }

    public static void configure(com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.CodeEditor editor) {
        editor.setStyleManager(STYLE_MANAGER);
        editor.setLanguage(LANGUAGE);
    }
}
