package com.TextAnalyzer;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Real-time text analysis tool.
 * @author Malith Dissanayake
 */
public class TextAnalyzer extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final String TOP_STATS = "topStats", WORDS = "Words", CHARS = "Characters",
        CHARS_NO_SPACE = "Characters (no space)", SENTENCES = "Sentences", PARAGRAPHS = "Paragraphs",
        UNIQUE_WORDS = "uniqueCount";

    private final JTextArea mainTextArea = new JTextArea();
    private final UndoManager undoManager = new UndoManager();
    private final Map<String, JLabel> statLabels = new HashMap<>();
    private final JToggleButton ignoreSymbolsToggle = new JToggleButton("Ignore symbols");
    private final List<String> trackingChars = new ArrayList<>(), trackingWords = new ArrayList<>();
    private final DefaultListModel<String> uniqueWordsListModel = new DefaultListModel<>();
    private final JPanel charCountPanel = new JPanel(), wordCountPanel = new JPanel();

    @FunctionalInterface
    interface DocumentUpdateListener extends DocumentListener {
        void onUpdate();
        default void insertUpdate(DocumentEvent e) { onUpdate(); }
        default void removeUpdate(DocumentEvent e) { onUpdate(); }
        default void changedUpdate(DocumentEvent e) { }
    }

    public TextAnalyzer() {
        setTitle("Text Analyzer");
        setSize(1024, 768);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel detailsPanel = createDetailsPanel();
        add(createTopPanel(), BorderLayout.NORTH);
        add(createCenterPanel(detailsPanel), BorderLayout.CENTER);
        add(detailsPanel, BorderLayout.EAST);

        mainTextArea.getDocument().addDocumentListener((DocumentUpdateListener) this::updateAnalytics);
        mainTextArea.getDocument().addUndoableEditListener(undoManager);
        ignoreSymbolsToggle.addActionListener(_ -> updateAnalytics());
        updateAnalytics();
    }

    private JPanel createTopPanel() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {{
            add(statLabels.computeIfAbsent(TOP_STATS, _ -> configure(new JLabel(" "),
                l -> l.setFont(new Font("Segoe UI", Font.BOLD, 32)))));
        }};
    }

    private JComponent createCenterPanel(JPanel detailsPanel) {
        return new JPanel(new BorderLayout(0, 5)) {{
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            Map.of("Paste", (Runnable) mainTextArea::paste, "Clear", () -> mainTextArea.setText(""),
                   "Undo", () -> { if (undoManager.canUndo()) undoManager.undo(); },
                   "Redo", () -> { if (undoManager.canRedo()) undoManager.redo(); }
            ).forEach((text, action) -> buttonPanel.add(createButton(text, _ -> action.run())));

            buttonPanel.add(configure(new JToggleButton("Wrap", true), t -> t.addActionListener(_ -> mainTextArea.setLineWrap(t.isSelected()))));
            buttonPanel.add(createButton("▶", e -> {
                detailsPanel.setVisible(!detailsPanel.isVisible());
                ((JButton) e.getSource()).setText(detailsPanel.isVisible() ? "▶" : "◀");
            }));
            add(buttonPanel, BorderLayout.NORTH);
            mainTextArea.setLineWrap(true);
            add(new JScrollPane(mainTextArea), BorderLayout.CENTER);
        }};
    }

    private JPanel createDetailsPanel() {
        JPanel p = configure(new JPanel(), panel -> {
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            panel.setPreferredSize(new Dimension(280, 0));
        });
        p.add(createStaticRow(configure(new JLabel("Details"), l -> l.setFont(l.getFont().deriveFont(Font.BOLD))), ignoreSymbolsToggle));
        p.add(new JSeparator());
        for (String key : List.of(WORDS, CHARS, CHARS_NO_SPACE, SENTENCES, PARAGRAPHS)) {
            p.add(createStaticRow(new JLabel(key), statLabels.computeIfAbsent(key, _ -> new JLabel("0"))));
            p.add(new JSeparator());
        }
        p.add(createTrackableSection("Character count", trackingChars, charCountPanel));
        p.add(new JSeparator());
        p.add(createTrackableSection("Word count", trackingWords, wordCountPanel));
        p.add(new JSeparator());
        p.add(createCollapsibleUniqueWordsSection());
        return p;
    }

    private JPanel createTrackableSection(String title, List<String> list, JPanel content) {
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        return new JPanel(new BorderLayout()) {{
            add(createStaticRow(new JLabel(title), createButton("+", _ -> { list.add(""); updateAnalytics(); })), BorderLayout.NORTH);
            add(content, BorderLayout.CENTER);
        }};
    }

    private JPanel createCollapsibleUniqueWordsSection() {
        JScrollPane scrollPane = configure(new JScrollPane(new JList<>(uniqueWordsListModel)), sp -> sp.setVisible(false));
        JLabel arrow = new JLabel("▼");
        JToggleButton toggle = configure(new JToggleButton("Unique words", false), t -> {
            t.setLayout(new BorderLayout());
            t.add(arrow, BorderLayout.WEST);
            t.add(statLabels.computeIfAbsent(UNIQUE_WORDS, _ -> new JLabel("0")), BorderLayout.EAST);
            t.addActionListener(_ -> {
                boolean selected = t.isSelected();
                scrollPane.setVisible(selected);
                arrow.setText(selected ? "▲" : "▼");
            });
        });
        return new JPanel(new BorderLayout()) {{ add(toggle, BorderLayout.NORTH); add(scrollPane, BorderLayout.CENTER); }};
    }

    private void updateAnalytics() {
        String rawText = mainTextArea.getText();
        String textForAnalysis = ignoreSymbolsToggle.isSelected() ? rawText.replaceAll("[^a-zA-Z0-9\\s]", "") : rawText;
        String trimmedRaw = rawText.trim();
        boolean isEmpty = trimmedRaw.isEmpty();
        String[] words = isEmpty ? new String[0] : textForAnalysis.trim().split("\\s+");
        Map<String, Long> uniqueCounts = Arrays.stream(words).filter(w -> !w.isEmpty())
            .collect(Collectors.groupingBy(String::toLowerCase, Collectors.counting()));

        statLabels.get(TOP_STATS).setText(String.format("%d Words %d Characters", words.length, rawText.length()));
        statLabels.get(WORDS).setText(String.valueOf(words.length));
        statLabels.get(CHARS).setText(String.valueOf(rawText.length()));
        statLabels.get(CHARS_NO_SPACE).setText(String.valueOf(rawText.replaceAll("\\s", "").length()));
        statLabels.get(SENTENCES).setText(String.valueOf(isEmpty ? 0 : trimmedRaw.split("[.!?]+").length));
        statLabels.get(PARAGRAPHS).setText(String.valueOf(isEmpty ? 0 : trimmedRaw.split("\\n\\s*\\n+").length));
        statLabels.get(UNIQUE_WORDS).setText(String.valueOf(uniqueCounts.size()));

        uniqueWordsListModel.clear();
        uniqueCounts.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(e -> uniqueWordsListModel.addElement(e.getKey() + " (" + e.getValue() + ")"));

        updateDynamicPanel(charCountPanel, trackingChars, textForAnalysis.toLowerCase(), true);
        updateDynamicPanel(wordCountPanel, trackingWords, textForAnalysis.toLowerCase(), false);
    }

    private void updateDynamicPanel(JPanel panel, List<String> items, String text, boolean isChar) {
        panel.removeAll();
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            JTextField termField = new JTextField(items.get(i), 10);
            JLabel countLabel = new JLabel("0");
            if (isChar) ((AbstractDocument) termField.getDocument()).setDocumentFilter(new SingleCharFilter());

            Runnable recalculator = () -> recalculateCount(countLabel, termField.getText(), text, isChar);
            termField.getDocument().addDocumentListener((DocumentUpdateListener) () -> { items.set(index, termField.getText()); recalculator.run(); });
            recalculator.run(); // Initial count

            panel.add(createStaticRow(createButton("x", _ -> { items.remove(index); updateAnalytics(); }), countLabel, termField));
        }
        panel.revalidate();
        panel.repaint();
    }

    private void recalculateCount(JLabel label, String term, String text, boolean isChar) {
        long count = term.isEmpty() ? 0 : isChar // Efficiently handles empty terms and dispatches to the correct logic
            ? text.chars().filter(c -> c == Character.toLowerCase(term.charAt(0))).count()
            : Arrays.stream(text.split("\\s+")).filter(w -> w.equalsIgnoreCase(term)).count();
        label.setText(String.valueOf(count));
    }

    private static <T extends JComponent> T configure(T c, Consumer<T> config) { config.accept(c); return c; }
    private static JButton createButton(String text, java.awt.event.ActionListener l) { return configure(new JButton(text), b -> b.addActionListener(l)); }
    private static JPanel createStaticRow(Component l, Component r) { return createStaticRow(l, r, null); }
    private static JPanel createStaticRow(Component left, Component right, Component center) {
        return configure(new JPanel(new BorderLayout(5, 0)), p -> {
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
            p.add(left, BorderLayout.WEST);
            p.add(right, BorderLayout.EAST);
            if (center != null) p.add(center, BorderLayout.CENTER);
        });
    }

    private static class SingleCharFilter extends DocumentFilter {
        @Override public void insertString(FilterBypass fb, int o, String s, AttributeSet a) throws BadLocationException { if (fb.getDocument().getLength() + s.length() <= 1) super.insertString(fb, o, s, a); }
        @Override public void replace(FilterBypass fb, int o, int l, String s, AttributeSet a) throws BadLocationException { if (fb.getDocument().getLength() - l + s.length() <= 1) super.replace(fb, o, l, s, a); }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ex) { ex.printStackTrace(); }
        SwingUtilities.invokeLater(() -> new TextAnalyzer().setVisible(true));
    }
}