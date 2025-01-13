package burp;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.event.MouseAdapter;  
import java.awt.event.MouseEvent;  
import java.awt.datatransfer.StringSelection;  
import javax.swing.JPopupMenu;  
import javax.swing.JMenuItem;  
import javax.swing.JButton; 

public class BurpExtender implements IBurpExtender, IHttpListener, ITab {
    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;
    private JPanel mainPanel;
    private DefaultListModel<String> urlListModel;
    private JTextArea commentArea,sensitiveArea;
    private Map<String, List<CommentInfo>> commentsMap;

    // Content-Type 白名单
    private static final Set<String> VALID_CONTENT_TYPES = new HashSet<>(Arrays.asList(
        "text/html",
        "text/xml",
    //    "text/plain",
        "text/javascript",
        "application/javascript",
        "application/x-javascript",
        "application/json",
        "application/xml",
        "application/xhtml+xml"
    ));

    private static final Map<String, Pattern> SENSITIVE_PATTERNS = new HashMap<>();  
    static {  
        // 用户凭据匹配  
        SENSITIVE_PATTERNS.put("用户名密码", Pattern.compile("(?i)(username|password|passwd|pwd|账号|密码|帐号)\\s*[=:]\\s*['\"](.*?)['\"]"));  
        // JWT匹配  
        SENSITIVE_PATTERNS.put("JWT Token", Pattern.compile("ey[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*"));  
        // URL匹配  
        SENSITIVE_PATTERNS.put("URL", Pattern.compile("(?i)(https?://[^\\s'\">]+)"));  
        // API Key匹配  
        SENSITIVE_PATTERNS.put("API Key", Pattern.compile("(?i)(api[_-]?key|app[_-]?key|secret[_-]?key)\\s*[=:]\\s*['\"](.*?)['\"]"));  
    }

    // 辅助方法：重复字符串
    private String repeatString(String str, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    private static class CommentInfo {
        String content;    // 注释内容
        String type;      // 注释类型
        String context;   // 注释上下文（所在标签或脚本）
        int lineNumber;   // 行号

        CommentInfo(String content, String type, String context, int lineNumber) {
            this.content = content;
            this.type = type;
            this.context = context;
            this.lineNumber = lineNumber;
        }
    }

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
        this.commentsMap = new HashMap<>();

        callbacks.setExtensionName("Comment Extractor");
        
        SwingUtilities.invokeLater(this::initializeUI);
        
        callbacks.registerHttpListener(this);
    }

    private void initializeUI() {  
        // 创建主面板  
        mainPanel = new JPanel(new BorderLayout());  
    
        // 创建主分割面板（左右分割）  
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);  
    
        // ====== 左侧面板配置 ======  
        JPanel leftPanel = new JPanel(new BorderLayout());  
        leftPanel.setBorder(BorderFactory.createTitledBorder("URL列表"));  
    
        // 创建顶部面板来容纳搜索框和清空按钮  
        JPanel topPanel = new JPanel(new BorderLayout());  
        
        // 添加搜索框  
        JTextField searchField = new JTextField();  
        searchField.setToolTipText("搜索URL");  
        topPanel.add(searchField, BorderLayout.CENTER);  
    
        // 添加清空按钮  
        JButton clearButton = new JButton("清空");  
        topPanel.add(clearButton, BorderLayout.EAST);  
        
        leftPanel.add(topPanel, BorderLayout.NORTH);  
    
        // 添加URL列表  
        urlListModel = new DefaultListModel<>();  
        JList<String> urlList = new JList<>(urlListModel);  
        urlList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);  
        
        // 创建右键菜单  
        JPopupMenu popupMenu = new JPopupMenu();  
        JMenuItem copyItem = new JMenuItem("复制URL");  
        popupMenu.add(copyItem);  
    
        // 添加右键菜单监听器  
        urlList.addMouseListener(new MouseAdapter() {  
            @Override  
            public void mousePressed(MouseEvent e) {  
                if (e.isPopupTrigger()) {  
                    showPopup(e);  
                }  
            }  
    
            @Override  
            public void mouseReleased(MouseEvent e) {  
                if (e.isPopupTrigger()) {  
                    showPopup(e);  
                }  
            }  
    
            private void showPopup(MouseEvent e) {  
                int index = urlList.locationToIndex(e.getPoint());  
                if (index != -1) {  
                    urlList.setSelectedIndex(index);  
                    popupMenu.show(urlList, e.getX(), e.getY());  
                }  
            }  
        });  
    
        // 复制菜单项的动作  
        copyItem.addActionListener(e -> {  
            String selectedUrl = urlList.getSelectedValue();  
            if (selectedUrl != null) {  
                StringSelection selection = new StringSelection(selectedUrl);  
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);  
            }  
        });  
    
        leftPanel.add(new JScrollPane(urlList), BorderLayout.CENTER);  
    
        // ====== 右侧面板配置 ======  
        JPanel rightPanel = new JPanel(new BorderLayout());  
        
        // 创建右侧的上下分割面板  
        JSplitPane rightSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);  
    
        // 上部分显示完整注释  
        JPanel commentPanel = new JPanel(new BorderLayout());  
        commentPanel.setBorder(BorderFactory.createTitledBorder("完整注释内容"));  
        commentArea = new JTextArea();  
        commentArea.setEditable(false);  
        commentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));  
        commentPanel.add(new JScrollPane(commentArea), BorderLayout.CENTER);  
    
        // 下部分显示敏感信息  
        JPanel sensitivePanel = new JPanel(new BorderLayout());  
        sensitivePanel.setBorder(BorderFactory.createTitledBorder("疑似敏感信息"));  
        sensitiveArea = new JTextArea();  
        sensitiveArea.setEditable(false);  
        sensitiveArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));  
        //sensitiveArea.setBackground(new Color(255, 245, 245));  
        sensitivePanel.add(new JScrollPane(sensitiveArea), BorderLayout.CENTER);  
    
        // 配置右侧分割面板  
        rightSplitPane.setTopComponent(commentPanel);  
        rightSplitPane.setBottomComponent(sensitivePanel);  
        rightSplitPane.setDividerLocation(400);  
    
        rightPanel.add(rightSplitPane, BorderLayout.CENTER);  
    
        // 配置主分割面板  
        mainSplitPane.setLeftComponent(leftPanel);  
        mainSplitPane.setRightComponent(rightPanel);  
        mainSplitPane.setDividerLocation(400);  
    
        // 将主分割面板添加到主面板  
        mainPanel.add(mainSplitPane, BorderLayout.CENTER);  
    
        // 清空按钮事件  
        clearButton.addActionListener(e -> {  
            urlListModel.clear();  
            commentsMap.clear();  
            commentArea.setText("");  
            sensitiveArea.setText("");  
        });  
    
        // URL选择监听器  
        urlList.addListSelectionListener(e -> {  
            if (!e.getValueIsAdjusting()) {  
                String selectedUrl = urlList.getSelectedValue();  
                if (selectedUrl != null) {  
                    updateCommentDisplay(selectedUrl);  
                }  
            }  
        });  
    
        // URL搜索功能  
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {  
            public void changedUpdate(javax.swing.event.DocumentEvent e) { search(); }  
            public void removeUpdate(javax.swing.event.DocumentEvent e) { search(); }  
            public void insertUpdate(javax.swing.event.DocumentEvent e) { search(); }  
            
            private void search() {  
                String searchText = searchField.getText().toLowerCase();  
                DefaultListModel<String> filteredModel = new DefaultListModel<>();  
                for (String url : Collections.list(urlListModel.elements())) {  
                    if (url.toLowerCase().contains(searchText)) {  
                        filteredModel.addElement(url);  
                    }  
                }  
                urlList.setModel(filteredModel);  
            }  
        });  
    
        // 完成UI配置  
        callbacks.customizeUiComponent(mainPanel);  
        callbacks.addSuiteTab(this);  
    }

    private Map<String, List<String>> detectSensitiveInfo(String content) {  
        Map<String, List<String>> results = new HashMap<>();  
        
        for (Map.Entry<String, Pattern> entry : SENSITIVE_PATTERNS.entrySet()) {  
            String type = entry.getKey();  
            Pattern pattern = entry.getValue();  
            Matcher matcher = pattern.matcher(content);  
            
            List<String> matches = new ArrayList<>();  
            while (matcher.find()) {  
                matches.add(matcher.group());  
            }  
            
            if (!matches.isEmpty()) {  
                results.put(type, matches);  
            }  
        }  
        
        return results;  
    }

    private void updateCommentDisplay(String url) {  
        List<CommentInfo> comments = commentsMap.get(url);  
        if (comments != null) {  
            // 更新注释显示区域  
            StringBuilder commentSb = new StringBuilder();  
            commentSb.append("URL: ").append(url).append("\n");  
            commentSb.append("发现 ").append(comments.size()).append(" 条注释\n");  
            commentSb.append(repeatString("=", 50)).append("\n\n");  
    
            for (CommentInfo comment : comments) {  
                commentSb.append("【类型】: ").append(comment.type).append("\n");  
                commentSb.append("【位置】: 第 ").append(comment.lineNumber).append(" 行\n");  
                if (comment.context != null && !comment.context.isEmpty()) {  
                    commentSb.append("【上下文】: ").append(comment.context).append("\n");  
                }  
                commentSb.append("【内容】:\n").append(comment.content).append("\n");  
                commentSb.append(repeatString("-", 50)).append("\n\n");  
            }  
            commentArea.setText(commentSb.toString());  
            commentArea.setCaretPosition(0);  
            
            // 更新敏感信息显示区域  
            StringBuilder sensitiveSb = new StringBuilder();  
            sensitiveSb.append("==== 发现的敏感信息 ====\n\n");  
            
            int sensitiveCount = 0;  
            for (CommentInfo comment : comments) {  
                Map<String, List<String>> sensitiveInfo = detectSensitiveInfo(comment.content);  
                if (!sensitiveInfo.isEmpty()) {  
                    sensitiveCount++;  
                    sensitiveSb.append("■ 在第 ").append(comment.lineNumber).append(" 行注释中发现敏感信息：\n");  
                    for (Map.Entry<String, List<String>> entry : sensitiveInfo.entrySet()) {  
                        sensitiveSb.append("  ◆ ").append(entry.getKey()).append(":\n");  
                        for (String info : entry.getValue()) {  
                            sensitiveSb.append("    - ").append(info).append("\n");  
                        }  
                    }  
                    sensitiveSb.append("\n");  
                }  
            }  
            
            if (sensitiveCount > 0) {  
                sensitiveSb.insert(0, String.format("总计发现 %d 处注释包含敏感信息\n\n", sensitiveCount));  
                sensitiveArea.setText(sensitiveSb.toString());  
            } else {  
                sensitiveArea.setText("未发现敏感信息");  
            }  
            sensitiveArea.setCaretPosition(0);  
        } else {  
            commentArea.setText("");  
            sensitiveArea.setText("");  
        }  
    }

    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo) {
        if (!messageIsRequest) {
            IResponseInfo responseInfo = helpers.analyzeResponse(messageInfo.getResponse());
            
            // 检查Content-Type
            String contentType = getContentType(responseInfo);
            if (!isValidContentType(contentType)) {
                return;
            }
            
            // 获取响应体
            byte[] response = messageInfo.getResponse();
            String responseStr = new String(Arrays.copyOfRange(response, responseInfo.getBodyOffset(), response.length));
            
            // 提取注释
            List<CommentInfo> comments = extractComments(responseStr);
            if (!comments.isEmpty()) {
                String url = helpers.analyzeRequest(messageInfo).getUrl().toString();
                commentsMap.put(url, comments);
                
                SwingUtilities.invokeLater(() -> {
                    if (!urlListModel.contains(url)) {
                        urlListModel.addElement(url);
                    }
                });
            }
        }
    }

    private String getContentType(IResponseInfo responseInfo) {
        for (String header : responseInfo.getHeaders()) {
            if (header.toLowerCase().startsWith("content-type:")) {
                // 提取Content-Type，去除可能存在的字符集信息
                String contentType = header.substring("content-type:".length()).trim();
                int semicolonIndex = contentType.indexOf(';');
                if (semicolonIndex != -1) {
                    contentType = contentType.substring(0, semicolonIndex).trim();
                }
                return contentType.toLowerCase();
            }
        }
        return null;
    }

    private boolean isValidContentType(String contentType) {
        return contentType != null && VALID_CONTENT_TYPES.contains(contentType.toLowerCase().trim());
    }

    private List<CommentInfo> extractComments(String content) {
        List<CommentInfo> comments = new ArrayList<>();
        String[] lines = content.split("\n");
        
        // HTML注释
        Pattern htmlPattern = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);
        Matcher htmlMatcher = htmlPattern.matcher(content);
        while (htmlMatcher.find()) {
            String commentContent = htmlMatcher.group(1).trim();
            String context = findContext(content, htmlMatcher.start(), 100);
            int lineNumber = getLineNumber(content, htmlMatcher.start());
            comments.add(new CommentInfo(commentContent, "HTML注释", context, lineNumber));
        }
        
        // JS多行注释
        Pattern jsMultiPattern = Pattern.compile("/\\*(.*?)\\*/", Pattern.DOTALL);
        Matcher jsMultiMatcher = jsMultiPattern.matcher(content);
        while (jsMultiMatcher.find()) {
            String commentContent = jsMultiMatcher.group(1).trim();
            String context = findContext(content, jsMultiMatcher.start(), 100);
            int lineNumber = getLineNumber(content, jsMultiMatcher.start());
            comments.add(new CommentInfo(commentContent, "JS多行注释", context, lineNumber));
        }
        
        // JS单行注释
        Pattern jsSinglePattern = Pattern.compile("//(.*)");
        for (int i = 0; i < lines.length; i++) {
            Matcher jsSingleMatcher = jsSinglePattern.matcher(lines[i]);
            while (jsSingleMatcher.find()) {
                String commentContent = jsSingleMatcher.group(1).trim();
                String context = lines[i].substring(0, jsSingleMatcher.start()).trim();
                comments.add(new CommentInfo(commentContent, "JS单行注释", context, i + 1));
            }
        }

        return comments;
    }

    private String findContext(String content, int position, int radius) {
        int start = Math.max(0, position - radius);
        int end = Math.min(content.length(), position + radius);
        String context = content.substring(start, end);
        
        // 查找最近的父标签
        Pattern tagPattern = Pattern.compile("<([a-zA-Z0-9]+)[^>]*>");
        Matcher matcher = tagPattern.matcher(context);
        String lastTag = null;
        while (matcher.find()) {
            if (matcher.start() < radius) { // 只考虑注释位置之前的标签
                lastTag = matcher.group(1);
            }
        }
        
        return lastTag != null ? "<" + lastTag + ">" : "";
    }

    private int getLineNumber(String content, int position) {
        return content.substring(0, position).split("\n").length;
    }

    @Override
    public String getTabCaption() {
        return "Comment Extractor";
    }

    @Override
    public Component getUiComponent() {
        return mainPanel;
    }
}