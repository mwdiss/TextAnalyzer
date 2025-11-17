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

/**Real-time text analysis tool.
 * @author Malith Dissanayake
 */
@SuppressWarnings("serial")
public class TextAnalyzer extends JFrame { //main app class
    private static final String TOP="top", WORDS="Words", CHARS="Characters", NO_SPACE="Characters (no space)",
        SENTENCES="Sentences", PARAGRAPHS="Paragraphs", UNIQUE="unique", COMMON_CHAR="Most Common Character", COMMON_WORD="Most Common Word";
    private final JTextArea area = new JTextArea(); //ui components
    private final UndoManager undo = new UndoManager();
    private final Map<String, JLabel> stats = new HashMap<>();
    private final JToggleButton ignoreSymbols = new JToggleButton("Ignore symbols");
    private final List<String> charTrackers = new ArrayList<>(), wordTrackers = new ArrayList<>(); //data models
    private final DefaultListModel<String> uniqueModel = new DefaultListModel<>();
    private final JPanel charTrackPanel = new JPanel(), wordTrackPanel = new JPanel();

    @FunctionalInterface //listener lambda helper
    interface OnUpdate extends DocumentListener {
        void update();
        default void insertUpdate(DocumentEvent e) { update(); }
        default void removeUpdate(DocumentEvent e) { update(); }
        default void changedUpdate(DocumentEvent e) {}
    }

    public TextAnalyzer() { //main app window
        setTitle("Text Analyzer");
        setSize(1024, 768);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel details = initDetails(); //build ui
        add(initTop(), BorderLayout.NORTH);
        add(initCenter(details), BorderLayout.CENTER);
        add(details, BorderLayout.EAST);
        area.getDocument().addDocumentListener((OnUpdate) this::analyze); //add listeners
        area.getDocument().addUndoableEditListener(undo);
        ignoreSymbols.addActionListener(_ -> analyze());
        analyze(); //initial run
    }
    private JPanel initTop() { //top bar
        return new JPanel(new FlowLayout(FlowLayout.LEFT,0,0)){{
        	add(config(stats.computeIfAbsent(TOP,_->new JLabel(" ")), l -> l.setFont(l.getFont().deriveFont(Font.BOLD, 16f))));
        }};
    }
    private JComponent initCenter(JPanel details) { //main text area, control buttons
        return new JPanel(new BorderLayout(0, 5)) {{
            JPanel btns = new JPanel(new BorderLayout());
            btns.add(btn("Paste", _ -> area.paste()), BorderLayout.WEST);
            btns.add(new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0)) {{
                add(btn("Clear", _->area.setText("")));
                add(btn("Undo", _->{if(undo.canUndo())undo.undo();}));
                add(btn("Redo", _->{if(undo.canRedo())undo.redo();}));
                add(Box.createHorizontalStrut(10));
                add(config(new JToggleButton("Wrap",true), t->t.addActionListener(_->area.setLineWrap(t.isSelected()))));
                add(btn("▶", e->{details.setVisible(!details.isVisible());((JButton)e.getSource()).setText(details.isVisible()?"▶":"◀");}));
            }}, BorderLayout.EAST);
            add(btns, BorderLayout.NORTH);
            area.setLineWrap(true);
            add(new JScrollPane(area), BorderLayout.CENTER);
        }};
    }
    private JPanel initDetails() { //right side panel
        JPanel p = config(new JPanel(),pan->{pan.setLayout(new BoxLayout(pan,BoxLayout.Y_AXIS)); pan.setPreferredSize(new Dimension(220, 0));});
        p.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        p.add(row(config(new JLabel("Details"),l->l.setFont(l.getFont().deriveFont(Font.BOLD))), ignoreSymbols)); p.add(new JSeparator());
        for(String key:List.of(WORDS,CHARS,NO_SPACE,SENTENCES,PARAGRAPHS,COMMON_CHAR,COMMON_WORD)){p.add(row(new JLabel(key),stats.computeIfAbsent(key,_->new JLabel("0")))); p.add(new JSeparator());}
        p.add(initTracker("Char count", charTrackers, charTrackPanel)); p.add(new JSeparator());
        p.add(initTracker("Word count", wordTrackers, wordTrackPanel)); p.add(new JSeparator());
        p.add(initUniqueWords());
        return p;
    }
    private JPanel initTracker(String title, List<String> items, JPanel content) { //custom tracker ui
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        return new JPanel(new BorderLayout()){{add(row(new JLabel(title),btn("+",_->{items.add("");analyze();})),BorderLayout.NORTH);add(content,BorderLayout.CENTER);}};
    }
    private JPanel initUniqueWords() { //unique words ui
        JScrollPane sp = config(new JScrollPane(new JList<>(uniqueModel)), s -> s.setVisible(false));
        JLabel arrow = new JLabel("▼");
        JToggleButton toggle = config(new JToggleButton("Unique words",false),t->{t.setLayout(new BorderLayout());t.add(arrow,BorderLayout.WEST);t.add(stats.computeIfAbsent(UNIQUE,_->new JLabel("0")),BorderLayout.EAST);t.addActionListener(_->{sp.setVisible(t.isSelected());arrow.setText(t.isSelected()?"▲":"▼");});});
        return new JPanel(new BorderLayout()){{add(toggle,BorderLayout.NORTH);add(sp,BorderLayout.CENTER);}};
    }
    private void analyze() { //main analysis logic
        String raw=area.getText(), txt=ignoreSymbols.isSelected()?raw.replaceAll("[^a-zA-Z0-9\\s]",""):raw; //get/clean text
        String trim = raw.trim(); boolean empty=trim.isEmpty();
        String[] words = empty ? new String[0] : txt.trim().split("\\s+");
        Map<String,Long> unique = Arrays.stream(words).filter(w->!w.isEmpty()).collect(Collectors.groupingBy(String::toLowerCase,TreeMap::new,Collectors.counting())); 
        //find most common char, word
        Optional<Map.Entry<String, Long>> commonCharEntry = txt.chars().filter(c->!Character.isWhitespace(c)).mapToObj(c->String.valueOf((char)c).toLowerCase()).collect(Collectors.groupingBy(c->c,Collectors.counting())).entrySet().stream().max(Map.Entry.comparingByValue());
        Optional<Map.Entry<String, Long>> commonWordEntry = unique.entrySet().stream().max(Map.Entry.comparingByValue());
        //update labels
        stats.get(TOP).setText(String.format("%d Words  %d Characters", words.length, raw.length()));
        stats.get(WORDS).setText(""+words.length);
        stats.get(CHARS).setText(""+raw.length());
        stats.get(NO_SPACE).setText(""+raw.replaceAll("\\s","").length());
        stats.get(SENTENCES).setText(""+(empty ? 0 : trim.split("[.!?]+").length));
        stats.get(PARAGRAPHS).setText(""+(empty ? 0 : trim.split("\\n\\s*\\n+").length));
        stats.get(COMMON_CHAR).setText(commonCharEntry.map(e -> String.format("%s (%d)", e.getKey(), e.getValue())).orElse("-"));
        stats.get(COMMON_WORD).setText(commonWordEntry.map(e -> String.format("%s (%d)", e.getKey(), e.getValue())).orElse("-"));
        stats.get(UNIQUE).setText(""+unique.size());
        //update lists
        uniqueModel.clear(); unique.forEach((k,v)->uniqueModel.addElement(k+" ("+v+")"));
        updateTracker(charTrackPanel, charTrackers, txt.toLowerCase(), true);
        updateTracker(wordTrackPanel, wordTrackers, txt.toLowerCase(), false);
    }
    private void updateTracker(JPanel p, List<String> items, String text, boolean isChar) {
        p.removeAll();
        for (int i=0; i < items.size(); i++) {
            final int index=i; JTextField term=new JTextField(items.get(i),10); JLabel count=new JLabel("0");
            if(isChar)((AbstractDocument)term.getDocument()).setDocumentFilter(new OneCharFilter()); //1-char limit
            Runnable r=()->recalc(count,term.getText(),text,isChar);
            term.getDocument().addDocumentListener((OnUpdate)()->{items.set(index,term.getText());r.run();}); r.run();
            p.add(row(btn("x",_->{items.remove(index);analyze();}),count,term));
        }
        p.revalidate(); p.repaint(); //refresh panel
    }
    private void recalc(JLabel lbl, String term, String text, boolean isChar) { //count for one tracker item
        lbl.setText(""+(term.isEmpty()?0:isChar?text.chars().filter(c->c==term.toLowerCase().charAt(0)).count():Arrays.stream(text.split("\\s+")).filter(w->w.equalsIgnoreCase(term)).count()));
    }
    //Helpers
    private static <T extends JComponent> T config(T c, Consumer<T> cfg) { cfg.accept(c); return c; }
    private static JButton btn(String txt, java.awt.event.ActionListener l) { return config(new JButton(txt), b->b.addActionListener(l)); }
    private static JPanel row(Component l, Component r) { return row(l, r, null); }
    private static JPanel row(Component l, Component r, Component c) {return config(new JPanel(new BorderLayout(5,0)),p->{p.setMaximumSize(new Dimension(Short.MAX_VALUE,25));p.add(l,BorderLayout.WEST);p.add(r,BorderLayout.EAST);if(c!=null)p.add(c,BorderLayout.CENTER);});}
    
    private static class OneCharFilter extends DocumentFilter { //textfield 1-char limit
        @Override public void insertString(FilterBypass fb,int o,String s,AttributeSet a)throws BadLocationException{if(fb.getDocument().getLength()+s.length()<=1)super.insertString(fb,o,s,a);}
        @Override public void replace(FilterBypass fb,int o,int l,String s,AttributeSet a)throws BadLocationException{if(fb.getDocument().getLength()-l+s.length()<=1)super.replace(fb,o,l,s,a);}
    }
    public static void main(String[] args) { //app start
        try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception e){ e.printStackTrace(); }
        SwingUtilities.invokeLater(()->new TextAnalyzer().setVisible(true));
    }
}