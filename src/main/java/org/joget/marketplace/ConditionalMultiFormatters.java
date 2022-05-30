package org.joget.marketplace;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListColumnFormat;
import org.joget.apps.datalist.model.DataListColumnFormatDefault;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.commons.util.TimeZoneUtil;
import org.joget.plugin.base.PluginManager;
import org.mozilla.javascript.Scriptable;

public class ConditionalMultiFormatters extends DataListColumnFormatDefault {
    private final static String MESSAGE_PATH = "messages/ConditionalMultiFormatters";
    
    public String getName() {
        return "Conditional Multi Formatters";
    }

    public String getVersion() {
        return "7.0.0";
    }

    public String getDescription() {
        return AppPluginUtil.getMessage("org.joget.marketplace.MultiConditionalFormatters.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    public String getLabel() {
        return AppPluginUtil.getMessage("org.joget.marketplace.MultiConditionalFormatters.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/datalist/ConditionalMultiFormatters.json", null, true, MESSAGE_PATH);
    }

    public String format(DataList dataList, DataListColumn column, Object row, Object value) {
        String[] conditionList = new String[]{"firstCondition","secondCondition","thirdCondition","fourthCondition","fifthCondition"};
        boolean debugMode = Boolean.parseBoolean((String)getProperty("debug"));
        boolean continueExecutionOnMatchedCondition = Boolean.parseBoolean((String)getProperty("continueExecutionOnMatchedCondition"));
        
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        
        for (String cond : conditionList) {
            String condition = (String)getProperty(cond);
            String processToolPropertyName = cond + "Formatter";
                    
            if (condition != null && !condition.isEmpty()){
                if(isMatch(condition, row)) {
                    if(debugMode){
                        LogUtil.info(getClass().getName(), "Matched formatter: " + cond);
                    }

                    Object objFormatter = getProperty(processToolPropertyName);
                    if (objFormatter != null && objFormatter instanceof Map) {
                        Map fvMap = (Map) objFormatter;
                        if (fvMap != null && fvMap.containsKey("className") && !fvMap.get("className").toString().isEmpty()) {
                            String className = fvMap.get("className").toString();
                            Map pluginProperties = (Map) fvMap.get("properties");

                            DataListColumnFormat p = (DataListColumnFormat)pluginManager.getPlugin(className);
                            p.setProperties(pluginProperties);

                            if(debugMode){
                                LogUtil.info(getClass().getName(), "Executing formatter: " + processToolPropertyName + " - " + className);
                            }
                            value = p.format(dataList, column, row, value);

                        }
                    }
                    if(!continueExecutionOnMatchedCondition){
                        break;
                    }
                }
            }
        }
        
        //exception, return original value
        return value.toString();
    }
    
    protected boolean isMatch(String rule, Object row) {
        Map variables = new HashMap();
        rule = prepareExpression(rule, row, variables);
        Object result = evaluateExpression(rule, variables);
        
        if (result != null && result instanceof Boolean) {
            return (Boolean) result;
        }
        return false;
    }
    
    protected String prepareExpression(String expr, Object row, Map variables) {
        Pattern pattern = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");
        Matcher matcher = pattern.matcher(expr);
        
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = evaluate(row, key);
            if (value != null) {
                variables.put(key, value);
                expr = expr.replaceAll(StringUtil.escapeRegex("{"+key+"}"), key);
            }
        }
        
        return expr;
    }
    
    protected Object evaluate(Object row, String propertyName) {
        if (propertyName != null && !propertyName.isEmpty()) {
            try {
                Object value = DataListService.evaluateColumnValueFromRow(row, propertyName); 
                
                //handle for lowercase propertyName
                if (value == null) {
                    value = DataListService.evaluateColumnValueFromRow(row, propertyName.toLowerCase());
                }
                if (value != null && value instanceof Date) {
                    value = TimeZoneUtil.convertToTimeZone((Date) value, null, AppUtil.getAppDateFormat());
                }
                return value;
            } catch (Exception e) {}
        }
        return null;
    }
    
    protected Object evaluateExpression(String expr, Map variables) {
        org.mozilla.javascript.Context cx = org.mozilla.javascript.Context.enter();
        Scriptable scope = cx.initStandardObjects(null);

        java.lang.Object eval;
        try {
            prepareContext(scope, variables);
            eval = cx.evaluateString(scope, expr, "", 1, null);
            return eval;
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, expr);
        } finally {
            org.mozilla.javascript.Context.exit();
        }
        return null;
    }
    
    protected void prepareContext(Scriptable scope, Map variables)
            throws Exception {
        Iterator iter = variables.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry me = (Map.Entry) iter.next();
            String key = me.getKey().toString();
            java.lang.Object value = me.getValue();
            scope.put(key, scope, value);
        }
    }
}
