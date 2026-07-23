/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class FavoritesManagementDialogStructureTest
{
    private static final Set<String> EDT_2026_1_CONFIGURATION_TYPES = Set.of(
        "Language", "Subsystem", "StyleItem", "Style", "CommonPicture", "Interface", "SessionParameter",
        "Role", "CommonTemplate", "FilterCriterion", "CommonModule", "CommonAttribute", "ExchangePlan",
        "XDTOPackage", "WebService", "HTTPService", "WSReference", "EventSubscription", "ScheduledJob",
        "SettingsStorage", "FunctionalOption", "FunctionalOptionsParameter", "DefinedType", "CommonCommand",
        "CommandGroup", "Constant", "CommonForm", "Catalog", "Document", "DocumentNumerator", "Sequence",
        "DocumentJournal", "Enum", "Report", "DataProcessor", "InformationRegister", "AccumulationRegister",
        "ChartOfCharacteristicTypes", "ChartOfAccounts", "AccountingRegister", "ChartOfCalculationTypes",
        "CalculationRegister", "BusinessProcess", "Task", "ExternalDataSource", "IntegrationService", "Bot",
        "WebSocketClient", "PaletteColor");

    private static final Set<String> COMMON_TYPES = Set.of(
        "Subsystem", "CommonModule", "SessionParameter", "Role", "CommonAttribute", "ExchangePlan",
        "FilterCriterion", "EventSubscription", "ScheduledJob", "Bot", "FunctionalOption",
        "FunctionalOptionsParameter", "DefinedType", "SettingsStorage", "CommonForm", "CommonCommand",
        "CommandGroup", "CommonTemplate", "CommonPicture", "XDTOPackage", "WebService", "HTTPService",
        "WSReference", "WebSocketClient", "IntegrationService", "PaletteColor", "StyleItem", "Style",
        "Language", "Interface");

    private static final List<String> COMMON_LABEL_ORDER = List.of(
        "Подсистемы", "Общие модули", "Параметры сеанса", "Роли", "Общие реквизиты", "Планы обмена",
        "Критерии отбора", "Подписки на события", "Регламентные задания", "Боты", "Функциональные опции",
        "Параметры функциональных опций", "Определяемые типы", "Хранилища настроек", "Общие формы",
        "Общие команды", "Группы команд", "Общие макеты", "Общие картинки", "XDTO-пакеты", "Web-сервисы",
        "HTTP-сервисы", "WS-ссылки", "WebSocket-клиенты", "Сервисы интеграции", "Цвета палитры",
        "Элементы стиля", "Стили", "Языки", "Интерфейсы");

    @Test
    public void everyConfigurationTypeHasExplicitRussianLabel() throws Exception
    {
        Map<String, String> labels = modelField("GROUP_LABELS");
        assertEquals(EDT_2026_1_CONFIGURATION_TYPES, labels.keySet());
    }

    @Test
    public void commonBranchContainsExpectedTypesInNavigatorOrder() throws Exception
    {
        Map<String, String> labels = modelField("GROUP_LABELS");
        Set<String> commonTypes = modelField("COMMON_KINDS");
        List<String> commonOrder = modelField("COMMON_GROUP_ORDER");

        assertEquals(COMMON_TYPES, commonTypes);
        assertEquals(COMMON_LABEL_ORDER, commonOrder);
        assertEquals(Set.copyOf(COMMON_LABEL_ORDER),
            commonTypes.stream().map(labels::get).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    public void nestedNavigatorGroupsHaveRussianLabels() throws Exception
    {
        Map<String, String> labels = modelField("NESTED_GROUP_LABELS");

        assertEquals("Реквизиты", labels.get("attributes"));
        assertEquals("Табличные части", labels.get("tabularSections"));
        assertEquals("Формы", labels.get("forms"));
        assertEquals("Команды", labels.get("commands"));
        assertEquals("Макеты", labels.get("templates"));
        assertEquals("Измерения", labels.get("dimensions"));
        assertEquals("Ресурсы", labels.get("resources"));
    }

    @Test
    public void searchUsesMinimumLengthAndBoundedAutoExpansion() throws Exception
    {
        assertEquals(Integer.valueOf(2), field("MIN_SEARCH_PATTERN_LENGTH"));
        assertEquals(Integer.valueOf(300), field("MAX_AUTO_EXPANDED_SEARCH_RESULTS"));
    }

    @Test
    public void editorCommandContributesToLastMainToolbarGroup() throws Exception
    {
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(FavoritesManagementDialogStructureTest.class.getResourceAsStream("/plugin.xml"))
            .getDocumentElement();
        NodeList contributions = root.getElementsByTagName("menuContribution");

        boolean found = false;
        for (int i = 0; i < contributions.getLength(); i++)
        {
            Element contribution = (Element)contributions.item(i);
            if ("toolbar:org.eclipse.ui.main.toolbar?before=PerspectiveSpacer"
                .equals(contribution.getAttribute("locationURI"))
                && containsToolbarCommand(contribution, "edt.metadata.favorites.editorToolbar",
                    "edt.metadata.favorites.toggleEditorPin"))
            {
                found = true;
                break;
            }
        }

        assertTrue("Editor pin command must be in the last main toolbar group", found);
    }

    @Test
    public void allDtEditorsUseCommonTypeAndNavigatorShortcut() throws Exception
    {
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(FavoritesManagementDialogStructureTest.class.getResourceAsStream("/plugin.xml"))
            .getDocumentElement();
        NodeList typeChecks = root.getElementsByTagName("instanceof");
        int commonEditorChecks = 0;
        for (int i = 0; i < typeChecks.getLength(); i++)
        {
            Element check = (Element)typeChecks.item(i);
            if ("com._1c.g5.v8.dt.ui.editor.IDtEditor".equals(check.getAttribute("value")))
            {
                commonEditorChecks++;
            }
        }

        assertEquals(3, commonEditorChecks);
        assertTrue("Ctrl+Alt+K must invoke the shared navigator/editor command",
            containsKeyBinding(root, "M1+M3+K", "edt.metadata.favorites.togglePin"));
    }

    private static boolean containsKeyBinding(Element root, String sequence, String commandId)
    {
        NodeList keys = root.getElementsByTagName("key");
        for (int i = 0; i < keys.getLength(); i++)
        {
            Element key = (Element)keys.item(i);
            if (sequence.equals(key.getAttribute("sequence"))
                && commandId.equals(key.getAttribute("commandId")))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean containsToolbarCommand(Element contribution, String toolbarId, String commandId)
    {
        NodeList toolbars = contribution.getElementsByTagName("toolbar");
        for (int i = 0; i < toolbars.getLength(); i++)
        {
            Element toolbar = (Element)toolbars.item(i);
            if (toolbarId.equals(toolbar.getAttribute("id")) && containsCommand(toolbar, commandId))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCommand(Element toolbar, String commandId)
    {
        NodeList commands = toolbar.getElementsByTagName("command");
        for (int i = 0; i < commands.getLength(); i++)
        {
            if (commandId.equals(((Element)commands.item(i)).getAttribute("commandId")))
            {
                return true;
            }
        }
        return false;
    }

    private static <T> T field(String name) throws Exception
    {
        return field(FavoritesManagementDialog.class, name);
    }


    private static <T> T modelField(String name) throws Exception
    {
        return field(FavoriteTreeModel.class, name);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Class<?> owner, String name) throws Exception
    {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (T)field.get(null);
    }
}
