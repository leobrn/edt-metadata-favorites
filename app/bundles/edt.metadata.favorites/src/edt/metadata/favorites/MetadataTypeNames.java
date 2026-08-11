/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


final class MetadataTypeNames
{
    record KindNames(String groupLabel, String fqnName)
    {
    }


    private static final Map<String, KindNames> KIND_NAMES = Map.ofEntries(
        Map.entry("Catalog", kind("Справочники", "Справочник")),
        Map.entry("Document", kind("Документы", "Документ")),
        Map.entry("DocumentJournal", kind("Журналы документов", "ЖурналДокументов")),
        Map.entry("DocumentNumerator", kind("Нумераторы документов", "НумераторДокументов")),
        Map.entry("Enum", kind("Перечисления", "Перечисление")),
        Map.entry("Report", kind("Отчёты", "Отчёт")),
        Map.entry("DataProcessor", kind("Обработки", "Обработка")),
        Map.entry("Constant", kind("Константы", "Константа")),
        Map.entry("InformationRegister", kind("Регистры сведений", "РегистрСведений")),
        Map.entry("AccumulationRegister", kind("Регистры накопления", "РегистрНакопления")),
        Map.entry("AccountingRegister", kind("Регистры бухгалтерии", "РегистрБухгалтерии")),
        Map.entry("CalculationRegister", kind("Регистры расчёта", "РегистрРасчёта")),
        Map.entry("ChartOfCharacteristicTypes",
            kind("Планы видов характеристик", "ПланВидовХарактеристик")),
        Map.entry("ChartOfAccounts", kind("Планы счетов", "ПланСчетов")),
        Map.entry("ChartOfCalculationTypes", kind("Планы видов расчёта", "ПланВидовРасчёта")),
        Map.entry("Sequence", kind("Последовательности", "Последовательность")),
        Map.entry("BusinessProcess", kind("Бизнес-процессы", "БизнесПроцесс")),
        Map.entry("Task", kind("Задачи", "Задача")),
        Map.entry("ExchangePlan", kind("Планы обмена", "ПланОбмена")),
        Map.entry("ExternalDataSource", kind("Внешние источники данных", "ВнешнийИсточникДанных")),
        Map.entry("Subsystem", kind("Подсистемы", "Подсистема")),
        Map.entry("CommonModule", kind("Общие модули", "ОбщийМодуль")),
        Map.entry("SessionParameter", kind("Параметры сеанса", "ПараметрСеанса")),
        Map.entry("Role", kind("Роли", "Роль")),
        Map.entry("CommonAttribute", kind("Общие реквизиты", "ОбщийРеквизит")),
        Map.entry("FilterCriterion", kind("Критерии отбора", "КритерийОтбора")),
        Map.entry("EventSubscription", kind("Подписки на события", "ПодпискаНаСобытие")),
        Map.entry("ScheduledJob", kind("Регламентные задания", "РегламентноеЗадание")),
        Map.entry("Bot", kind("Боты", "Бот")),
        Map.entry("FunctionalOption", kind("Функциональные опции", "ФункциональнаяОпция")),
        Map.entry("FunctionalOptionsParameter",
            kind("Параметры функциональных опций", "ПараметрФункциональныхОпций")),
        Map.entry("DefinedType", kind("Определяемые типы", "ОпределяемыйТип")),
        Map.entry("SettingsStorage", kind("Хранилища настроек", "ХранилищеНастроек")),
        Map.entry("CommonForm", kind("Общие формы", "ОбщаяФорма")),
        Map.entry("CommonCommand", kind("Общие команды", "ОбщаяКоманда")),
        Map.entry("CommandGroup", kind("Группы команд", "ГруппаКоманд")),
        Map.entry("CommonTemplate", kind("Общие макеты", "ОбщийМакет")),
        Map.entry("CommonPicture", kind("Общие картинки", "ОбщаяКартинка")),
        Map.entry("XDTOPackage", kind("XDTO-пакеты", "ПакетXDTO")),
        Map.entry("WebService", kind("Web-сервисы", "WebСервис")),
        Map.entry("HTTPService", kind("HTTP-сервисы", "HTTPСервис")),
        Map.entry("WSReference", kind("WS-ссылки", "WSСсылка")),
        Map.entry("WebSocketClient", kind("WebSocket-клиенты", "КлиентWebSocket")),
        Map.entry("IntegrationService", kind("Сервисы интеграции", "СервисИнтеграции")),
        Map.entry("PaletteColor", kind("Цвета палитры", "ЦветПалитры")),
        Map.entry("StyleItem", kind("Элементы стиля", "ЭлементСтиля")),
        Map.entry("Style", kind("Стили", "Стиль")),
        Map.entry("Language", kind("Языки", "Язык")),
        Map.entry("Interface", kind("Интерфейсы", "Интерфейс")));


    private static final Map<String, String> NESTED_KIND_NAMES = Map.ofEntries(
        Map.entry("Реквизит", "Attribute"),
        Map.entry("Реквизиты", "Attribute"),
        Map.entry("Табличная часть", "TabularSection"),
        Map.entry("Табличные части", "TabularSection"),
        Map.entry("ТабличнаяЧасть", "TabularSection"),
        Map.entry("Форма", "Form"),
        Map.entry("Формы", "Form"),
        Map.entry("Команда", "Command"),
        Map.entry("Команды", "Command"),
        Map.entry("Макет", "Template"),
        Map.entry("Макеты", "Template"),
        Map.entry("Измерение", "Dimension"),
        Map.entry("Измерения", "Dimension"),
        Map.entry("Ресурс", "Resource"),
        Map.entry("Ресурсы", "Resource"),
        Map.entry("Перерасчёт", "Recalculation"),
        Map.entry("Перерасчёты", "Recalculation"),
        Map.entry("Значение", "EnumValue"),
        Map.entry("Значения", "EnumValue"),
        Map.entry("Графа", "Column"),
        Map.entry("Графы", "Column"),
        Map.entry("Реквизит адресации", "AddressingAttribute"),
        Map.entry("Реквизиты адресации", "AddressingAttribute"),
        Map.entry("Операция", "Operation"),
        Map.entry("Операции", "Operation"),
        Map.entry("Поле", "Field"),
        Map.entry("Поля", "Field"),
        Map.entry("Параметр", "Parameter"),
        Map.entry("Параметры", "Parameter"),
        Map.entry("ШаблонURL", "URLTemplate"),
        Map.entry("Шаблон URL", "URLTemplate"),
        Map.entry("Шаблоны URL", "URLTemplate"),
        Map.entry("Метод", "Method"),
        Map.entry("Методы", "Method"));


    private static final Map<String, String> KINDS_BY_TYPE_NAME = buildKindsByTypeName();


    private static final Set<String> NESTED_SEARCH_KEYS = buildNestedSearchKeys();


    private static final String COMMON_OWNER_PREFIX = "common";


    private static final Set<String> OWNER_PREFIXES = buildOwnerPrefixes();

    private MetadataTypeNames()
    {
    }


    static Map<String, KindNames> kindNames()
    {
        return KIND_NAMES;
    }


    static String groupLabel(String kind)
    {
        KindNames names = KIND_NAMES.get(kind);
        return names == null ? kind : names.groupLabel();
    }


    static String kindForTypeName(String normalizedSegment)
    {
        return normalizedSegment.isEmpty() ? null : KINDS_BY_TYPE_NAME.get(searchKey(normalizedSegment));
    }


    static boolean isNestedTypeName(String normalizedSegment)
    {
        return !normalizedSegment.isEmpty() && NESTED_SEARCH_KEYS.contains(searchKey(normalizedSegment));
    }

    static boolean isClassNameOfNestedKind(String normalizedSegment, String kind)
    {
        if (normalizedSegment.equals(kind))
        {
            return true;
        }
        if (!normalizedSegment.endsWith(kind))
        {
            return false;
        }
        return OWNER_PREFIXES
            .contains(normalizedSegment.substring(0, normalizedSegment.length() - kind.length()));
    }

    private static Set<String> buildOwnerPrefixes()
    {
        Set<String> nested = new HashSet<>();
        NESTED_KIND_NAMES.values().forEach(kind -> nested.add(kind.toLowerCase(Locale.ROOT)));

        Set<String> prefixes = new HashSet<>();
        prefixes.add(COMMON_OWNER_PREFIX);
        KIND_NAMES.keySet().forEach(kind -> {
            String owner = kind.toLowerCase(Locale.ROOT);
            prefixes.add(owner);
            nested.forEach(nestedKind -> prefixes.add(owner + nestedKind));
        });
        return Set.copyOf(prefixes);
    }

    private static Map<String, String> buildKindsByTypeName()
    {
        Map<String, String> kinds = new HashMap<>();
        KIND_NAMES.forEach((kind, names) -> {
            String searchedKind = kind.toLowerCase(Locale.ROOT);
            kinds.put(searchKey(names.groupLabel()), searchedKind);
            kinds.put(searchKey(names.fqnName()), searchedKind);
            kinds.put(searchedKind, searchedKind);
        });
        NESTED_KIND_NAMES.forEach((typeName, kind) -> {
            String searchedKind = kind.toLowerCase(Locale.ROOT);
            kinds.put(searchKey(typeName), searchedKind);
            kinds.put(searchedKind, searchedKind);
        });
        return Map.copyOf(kinds);
    }

    private static Set<String> buildNestedSearchKeys()
    {
        Set<String> keys = new HashSet<>();
        NESTED_KIND_NAMES.forEach((typeName, kind) -> {
            keys.add(searchKey(typeName));
            keys.add(kind.toLowerCase(Locale.ROOT));
        });
        return Set.copyOf(keys);
    }

    private static String searchKey(String value)
    {
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private static KindNames kind(String groupLabel, String fqnName)
    {
        return new KindNames(groupLabel, fqnName);
    }
}
