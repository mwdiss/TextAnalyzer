package com.TextAnalyzer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A dynamic, real-time text analysis tool built to a detailed UI specification.
 * It provides live analytics, inline custom counters, and a high-performance UI.
 * @author Malith Dissanayake
 */
public class TextAnalyzer extends JFrame {
    private static final long serialVersionUID = 1L;
    private final JTextArea mainTextArea = new JTextArea();
    private final UndoManager undoManager = new UndoManager();
    private final Map<String, JLabel> statLabels = new HashMap<>();
    private final JToggleButton ignoreSymbolsToggle = new JToggleButton("Ignore symbols");
    private final JPanel detailsPanel, charCountPanel = createDynamicPanel(), wordCountPanel = createDynamicPanel();
    private final List<String> trackingChars = new ArrayList<>(), trackingWords = new ArrayList<>();
    private final DefaultListModel<String> uniqueWordsListModel = new DefaultListModel<>();

    public TextAnalyzer() {
        setTitle("Text Analyzer");
        setSize(1024, 768);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        detailsPanel = createDetailsPanel();
        Container cp = getContentPane();
        cp.setLayout(new BorderLayout(15, 10));
        ((JPanel) cp).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        cp.add(createTopPanel(), BorderLayout.NORTH);
        cp.add(createCenterPanel(), BorderLayout.CENTER);
        cp.add(detailsPanel, BorderLayout.EAST);

        // --- Event Listeners ---
        mainTextArea.getDocument().addDocumentListener(createAnalyticsListener(this::updateAnalytics));
        mainTextArea.getDocument().addUndoableEditListener(undoManager);
        ignoreSymbolsToggle.addActionListener(_ -> updateAnalytics());
        updateAnalytics();
    }

    private JPanel createTopPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.add(statLabels.computeIfAbsent("topStats", _ -> new JLabel("0 Words 0 Characters")));
        statLabels.get("topStats").setFont(new Font("Segoe UI", Font.BOLD, 32));
        return p;
    }

    private JPanel createCenterPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(new JButton("Copy") {{ addActionListener(_ -> mainTextArea.copy()); }});
        buttonPanel.add(new JButton("Cut") {{ addActionListener(_ -> mainTextArea.cut()); }});
        buttonPanel.add(new JButton("Paste") {{ addActionListener(_ -> mainTextArea.paste()); }});
        buttonPanel.add(new JButton("Clear") {{ addActionListener(_ -> mainTextArea.setText("")); }});
        buttonPanel.add(new JButton("Undo") {{ addActionListener(_ -> { if (undoManager.canUndo()) undoManager.undo(); }); }});
        buttonPanel.add(new JButton("Redo") {{ addActionListener(_ -> { if (undoManager.canRedo()) undoManager.redo(); }); }});

        JToggleButton wrapToggle = new JToggleButton("Wrap", true) {{ addActionListener(_ -> mainTextArea.setLineWrap(isSelected())); }};
        buttonPanel.add(wrapToggle);

        JButton collapseBtn = new JButton("▶");
        collapseBtn.addActionListener(_ -> {
            detailsPanel.setVisible(!detailsPanel.isVisible());
            collapseBtn.setText(detailsPanel.isVisible() ? "▶" : "◀");
        });
        buttonPanel.add(collapseBtn);

        JPanel p = new JPanel(new BorderLayout(0, 5));
        p.add(buttonPanel, BorderLayout.NORTH);
        p.add(new JScrollPane(mainTextArea), BorderLayout.CENTER);
        return p;
    }

    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel header = new JPanel(new BorderLayout()) {{ add(new JLabel("Details"){{ setFont(getFont().deriveFont(Font.BOLD)); }}, BorderLayout.WEST); add(ignoreSymbolsToggle, BorderLayout.EAST); setMaximumSize(new Dimension(Integer.MAX_VALUE, ignoreSymbolsToggle.getPreferredSize().height)); }};
        panel.add(header);
        panel.add(new JSeparator());

        for (String key : new String[]{"Words", "Characters", "Characters (without space)", "Sentences", "Paragraphs"}) {
            addStaticRow(panel, key);
            panel.add(new JSeparator());
        }

        panel.add(createTrackableSection("Character count", trackingChars, charCountPanel));
        panel.add(new JSeparator());
        panel.add(createTrackableSection("Word count", trackingWords, wordCountPanel));
        panel.add(new JSeparator());
        panel.add(createCollapsibleSection()); // This will now expand correctly.

        panel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        panel.setPreferredSize(new Dimension(280, 0));
        return panel;
    }

    private void addStaticRow(JPanel parent, String title) {
        JPanel row = new JPanel(new BorderLayout()) {{ setMaximumSize(new Dimension(Integer.MAX_VALUE, 25)); add(new JLabel(title), BorderLayout.WEST); add(statLabels.computeIfAbsent(title, _ -> new JLabel("0")), BorderLayout.EAST); }};
        parent.add(row);
    }

    private JPanel createTrackableSection(String title, List<String> list, JPanel content) {
        JButton addBtn = new JButton("+");
        addBtn.addActionListener(_ -> { list.add(""); updateAnalytics(); });
        JPanel header = new JPanel(new BorderLayout()) {{ add(new JLabel(title), BorderLayout.WEST); add(addBtn, BorderLayout.EAST); }};
        return new JPanel(new BorderLayout()) {{ add(header, BorderLayout.NORTH); add(content, BorderLayout.CENTER); setMaximumSize(new Dimension(Integer.MAX_VALUE, 1000)); }};
    }

    private JPanel createCollapsibleSection() {
        JList<String> uniqueList = new JList<>(uniqueWordsListModel);
        JScrollPane scrollPane = new JScrollPane(uniqueList) {{ setVisible(false); }};

        JToggleButton toggle = new JToggleButton("Unique words", false);
        toggle.setLayout(new BorderLayout());
        toggle.add(statLabels.computeIfAbsent("uniqueCount", _->new JLabel("0")), BorderLayout.EAST);
        toggle.add(new JLabel("▼"), BorderLayout.WEST);
        toggle.addActionListener(_ -> {
            boolean visible = toggle.isSelected();
            scrollPane.setVisible(visible);
            ((JLabel)toggle.getComponent(1)).setText(visible ? "▲" : "▼");
        });

        return new JPanel(new BorderLayout()) {{ add(toggle, BorderLayout.NORTH); add(scrollPane, BorderLayout.CENTER); }};
    }

    private void updateAnalytics() {
        String rawText = mainTextArea.getText();
        if (rawText.equals("Type here...")) rawText = "";
        String text = ignoreSymbolsToggle.isSelected() ? rawText.replaceAll("[^a-zA-Z0-9\\s]", "") : rawText;
        String[] words = text.trim().isEmpty() ? new String[0] : text.trim().split("\\s+");

        statLabels.get("topStats").setText(words.length + " Words " + rawText.length() + " Characters");
        statLabels.get("Words").setText(String.valueOf(words.length));
        statLabels.get("Characters").setText(String.valueOf(rawText.length()));
        statLabels.get("Characters (without space)").setText(String.valueOf(rawText.replaceAll("\\s", "").length()));
        statLabels.get("Sentences").setText(String.valueOf(rawText.trim().isEmpty() ? 0 : rawText.trim().split("[.!?]+").length));
        statLabels.get("Paragraphs").setText(String.valueOf(rawText.trim().isEmpty() ? 0 : rawText.trim().split("\\n\\s*\\n+").length));

        updateDynamicPanel(charCountPanel, trackingChars, ignoreSymbolsToggle.isSelected() ? text.toLowerCase() : rawText.toLowerCase(), true);
        updateDynamicPanel(wordCountPanel, trackingWords, rawText.replaceAll("[^a-zA-Z0-9\\s]", "").toLowerCase(), false);

        Map<String, Long> uniqueCounts = Arrays.stream(rawText.replaceAll("[^a-zA-Z0-9\\s]", "").toLowerCase().split("\\s+")).filter(w -> !w.isEmpty()).collect(Collectors.groupingBy(w -> w, Collectors.counting()));
        statLabels.get("uniqueCount").setText(String.valueOf(uniqueCounts.size()));

        uniqueWordsListModel.clear();
        uniqueCounts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> uniqueWordsListModel.addElement(e.getKey() + " (" + e.getValue() + ")"));
    }

    private void updateDynamicPanel(JPanel panel, List<String> items, String text, boolean isChar) {
        panel.removeAll();
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            String item = items.get(i);
            JTextField termField = new JTextField(item);
            JLabel countLabel = new JLabel("0");

            if (isChar) ((AbstractDocument) termField.getDocument()).setDocumentFilter(new SingleCharFilter());

            termField.getDocument().addDocumentListener(createAnalyticsListener(() -> {
                String currentTerm = termField.getText();
                items.set(index, currentTerm); // Update the model
                recalculateCount(countLabel, currentTerm, text, isChar); // Update only this row's count
            }));

            recalculateCount(countLabel, item, text, isChar); // Initial calculation

            JPanel row = new JPanel(new BorderLayout(5, 0)) {{ setMaximumSize(new Dimension(Integer.MAX_VALUE, 24)); }};
            JButton removeBtn = new JButton("x") {{ addActionListener(_ -> { items.remove(index); updateAnalytics(); }); }};

            row.add(removeBtn, BorderLayout.WEST);
            row.add(termField, BorderLayout.CENTER);
            row.add(countLabel, BorderLayout.EAST);
            panel.add(row);
        }
        panel.revalidate(); panel.repaint();
    }

    private void recalculateCount(JLabel label, String term, String text, boolean isChar) {
        long count = 0;
        if (!text.trim().isEmpty() && !term.isEmpty()) {
            count = isChar ? text.chars().filter(c -> c == term.toLowerCase().charAt(0)).count()
                           : Arrays.stream(text.split("\\s+")).filter(w -> w.equalsIgnoreCase(term)).count();
        }
        label.setText(String.valueOf(count));
    }

    private DocumentListener createAnalyticsListener(Runnable... onUpdate) {
        return new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { for(Runnable r : onUpdate) r.run(); }
            public void removeUpdate(DocumentEvent e) { for(Runnable r : onUpdate) r.run(); }
            public void changedUpdate(DocumentEvent e) {}
        };
    }

    private JPanel createDynamicPanel() { return new JPanel() {{ setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }}; }

    private static class SingleCharFilter extends DocumentFilter {
        public void insertString(FilterBypass fb, int offset, String str, AttributeSet attr) throws BadLocationException { if (str != null && fb.getDocument().getLength() + str.length() <= 1) super.insertString(fb, offset, str, attr); }
        public void replace(FilterBypass fb, int offset, int length, String str, AttributeSet attrs) throws BadLocationException { if (str != null && fb.getDocument().getLength() - length + str.length() <= 1) super.replace(fb, offset, length, str, attrs); }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ex) { ex.printStackTrace(); }
        SwingUtilities.invokeLater(() -> new TextAnalyzer().setVisible(true));
    }
}