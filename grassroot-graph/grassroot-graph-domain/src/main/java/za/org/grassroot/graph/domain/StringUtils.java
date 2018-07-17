package za.org.grassroot.graph.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class StringUtils {

    private StringUtils() {}

    public static String[] addStringsToArray(String[] array, Collection<String> stringsToAdd) {
        if (array != null)
            stringsToAdd.addAll(Arrays.asList(array));
        return stringsToAdd.toArray(new String[stringsToAdd.size()]);
    }

    public static String[] removeStringsFromArray(String[] array, Collection<String> stringsToRemove) {
        if (array == null) return null;
        List<String> stringsToKeep = Arrays.asList(array);
        stringsToKeep.removeAll(stringsToRemove);
        return stringsToKeep.toArray(new String[stringsToKeep.size()]);
    }

}
