import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Main {

    // =========================================================
    // PERSISTENT VECTOR (ORIGINAL LOGIC PRESERVED)
    // =========================================================
    static class PersistentVector {
        private static final int BRANCHING = 32;
        private static final int MASK = BRANCHING - 1;

        private final int size;
        private final int shift;
        private final Node root;
        private final Object[] tail;

        static final class Node {
            final Object[] array;
            Node(Object[] array) { this.array = array; }
        }

        PersistentVector() {
            this.size = 0;
            this.shift = 5;
            this.root = new Node(new Object[BRANCHING]);
            this.tail = new Object[0];
        }

        private PersistentVector(int size, int shift, Node root, Object[] tail) {
            this.size = size;
            this.shift = shift;
            this.root = root;
            this.tail = tail;
        }

        public int size() {
            return size;
        }

        // ================= nth =================
        public Object get(int idx) {
            if (idx < 0 || idx >= size) return null;

            if (idx >= tailOffset()) {
                return tail[idx & MASK];
            }

            Node node = root;
            for (int level = shift; level > 0; level -= 5) {
                node = (Node) node.array[(idx >>> level) & MASK];
            }
            return node.array[idx & MASK];
        }

        // ================= conj =================
        public PersistentVector push(Object value) {
            if (tail.length < BRANCHING) {
                Object[] newTail = new Object[tail.length + 1];
                System.arraycopy(tail, 0, newTail, 0, tail.length);
                newTail[tail.length] = value;
                return new PersistentVector(size + 1, shift, root, newTail);
            }

            Node tailNode = new Node(tail);
            Node newRoot;
            int newShift = shift;

            if ((size >>> 5) > (1 << shift)) {
                Object[] arr = new Object[BRANCHING];
                arr[0] = root;
                arr[1] = newPath(shift, tailNode);
                newRoot = new Node(arr);
                newShift += 5;
            } else {
                newRoot = pushTail(shift, root, tailNode);
            }

            return new PersistentVector(
                    size + 1,
                    newShift,
                    newRoot,
                    new Object[]{value}
            );
        }

        // ================= assoc =================
        public PersistentVector assoc(int idx, Object value) {
            if (idx < 0 || idx >= size) return null;

            if (idx >= tailOffset()) {
                Object[] newTail = tail.clone();
                newTail[idx & MASK] = value;
                return new PersistentVector(size, shift, root, newTail);
            }

            return new PersistentVector(
                    size,
                    shift,
                    doAssoc(shift, root, idx, value),
                    tail
            );
        }

        // ================= pop =================
        public PersistentVector pop() {
            if (size == 0) return this;
            if (size == 1) return new PersistentVector();

            if (tail.length > 1) {
                Object[] newTail = new Object[tail.length - 1];
                System.arraycopy(tail, 0, newTail, 0, newTail.length);
                return new PersistentVector(size - 1, shift, root, newTail);
            }

            Object newTailVal = get(size - 2);
            Node newRoot = popTail(shift, root);
            int newShift = shift;

            if (shift > 5 && newRoot.array[1] == null) {
                newRoot = (Node) newRoot.array[0];
                newShift -= 5;
            }

            return new PersistentVector(
                    size - 1,
                    newShift,
                    newRoot,
                    new Object[]{newTailVal}
            );
        }

        // ================= helpers =================
        private int tailOffset() {
            return size - tail.length;
        }

        private Node newPath(int level, Node node) {
            if (level == 0) return node;
            Object[] arr = new Object[BRANCHING];
            arr[0] = newPath(level - 5, node);
            return new Node(arr);
        }

        private Node pushTail(int level, Node parent, Node tailNode) {
            Object[] arr = parent.array.clone();
            int subIdx = ((size - 1) >>> level) & MASK;

            Node child = (Node) arr[subIdx];
            arr[subIdx] = (level == 5)
                    ? tailNode
                    : pushTail(level - 5, child, tailNode);

            return new Node(arr);
        }

        private Node doAssoc(int level, Node node, int idx, Object value) {
            Object[] arr = node.array.clone();
            if (level == 0) {
                arr[idx & MASK] = value;
            } else {
                int subIdx = (idx >>> level) & MASK;
                arr[subIdx] = doAssoc(level - 5, (Node) arr[subIdx], idx, value);
            }
            return new Node(arr);
        }

        private Node popTail(int level, Node node) {
            int subIdx = ((size - 2) >>> level) & MASK;

            if (level > 5) {
                Node newChild = popTail(level - 5, (Node) node.array[subIdx]);
                if (newChild == null && subIdx == 0) return null;

                Object[] arr = node.array.clone();
                arr[subIdx] = newChild;
                return new Node(arr);
            }

            if (subIdx == 0) return null;

            Object[] arr = node.array.clone();
            arr[subIdx] = null;
            return new Node(arr);
        }

        // ================= ORIGINAL toString (NO TRUNCATION) =================
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < size; i++) {
                sb.append(get(i));
                if (i < size - 1) sb.append(", ");
            }
            return sb.append("]").toString();
        }
    }

    // =========================================================
    // GUI - SIMPLIFIED FOR PRESENTATION
    // =========================================================
    static class App extends JFrame {

        PersistentVector current = new PersistentVector();
        List<PersistentVector> versions = new ArrayList<>();

        DefaultListModel<String> versionModel = new DefaultListModel<>();
        JList<String> versionList = new JList<>(versionModel);

        JTextArea displayArea = new JTextArea();

        App() {
            super("Persistent Vector Demo");
            versions.add(current);

            setSize(1000, 600);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout());

            // ================= LEFT PANEL - Controls =================
            JPanel left = new GradientPanel();
            left.setLayout(new GridBagLayout());
            left.setPreferredSize(new Dimension(250, 600));
            left.setBorder(new EmptyBorder(20, 20, 20, 20));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = GridBagConstraints.RELATIVE;
            gbc.insets = new Insets(10, 0, 10, 0);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // Buttons with emojis for visual appeal
            RoundButton addBtn = new RoundButton("Push (Add)");
            RoundButton updBtn = new RoundButton("Update");
            RoundButton delBtn = new RoundButton("Pop (Remove Last)");
            RoundButton nthBtn = new RoundButton("Access Element");

            JLabel infoLabel = new JLabel("<html><div style='text-align: center;'><strong>Clojure Style <br>Persistent Vectors</strong></div></html>");
            infoLabel.setForeground(Color.WHITE);
            infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            left.add(infoLabel, gbc);

            left.add(addBtn, gbc);
            left.add(updBtn, gbc);
            left.add(delBtn, gbc);
            left.add(nthBtn, gbc);

            // Add a simple explanation label


            add(left, BorderLayout.WEST);

            // ================= RIGHT PANEL - Display =================
            JPanel right = new JPanel(new BorderLayout(10, 0));
            right.setBorder(new EmptyBorder(20, 10, 20, 20));

            // Version History Panel
            JPanel versionPanel = new JPanel(new BorderLayout());
            versionPanel.setBorder(BorderFactory.createTitledBorder("📜 Version History"));
            versionPanel.setPreferredSize(new Dimension(350, 0));

            versionList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            versionPanel.add(new JScrollPane(versionList));

            // Current Vector Display
            JPanel displayPanel = new JPanel(new BorderLayout());
            displayPanel.setBorder(BorderFactory.createTitledBorder("🔍 Current Vector"));

            displayArea.setFont(new Font("Consolas", Font.PLAIN, 14));
            displayArea.setEditable(false);
            displayArea.setBackground(new Color(240, 240, 245));
            displayPanel.add(new JScrollPane(displayArea));

            right.add(versionPanel, BorderLayout.WEST);
            right.add(displayPanel, BorderLayout.CENTER);

            add(right, BorderLayout.CENTER);

            // ================= BUTTON ACTIONS =================
            addBtn.addActionListener(e -> {
                String v = JOptionPane.showInputDialog(this, "Enter value to add:");
                if (v != null && !v.isBlank()) {
                    current = current.push(v);
                    versions.add(current);
                    refreshVersions();
                }
            });

            updBtn.addActionListener(e -> {
                int i = askIndex();
                if (i >= 0) {
                    String v = JOptionPane.showInputDialog(this, "New value:");
                    if (v != null && !v.isBlank()) {
                        current = current.assoc(i, v);
                        versions.add(current);
                        refreshVersions();
                    }
                }
            });

            delBtn.addActionListener(e -> {
                current = current.pop();
                versions.add(current);
                refreshVersions();
            });

            nthBtn.addActionListener(e -> {
                int i = askIndex();
                if (i >= 0)
                    JOptionPane.showMessageDialog(this,
                            "Element at index " + i + " = " + current.get(i));
            });

            // Click on version to view it
            versionList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int idx = versionList.getSelectedIndex();
                    if (idx >= 0 && idx < versions.size()) {
                        current = versions.get(idx);
                        updateDisplay();
                    }
                }
            });

            // Initialize display
            refreshVersions();
            setVisible(true);
        }

        // Helper to ask for index
        int askIndex() {
            try {
                String input = JOptionPane.showInputDialog(
                        this, "Index (0-" + (current.size() - 1) + "):"
                );
                if (input == null) return -1;
                int idx = Integer.parseInt(input);
                return (idx >= 0 && idx < current.size()) ? idx : -1;
            } catch (Exception e) {
                return -1;
            }
        }

        // ================= REFRESH VERSIONS - UPDATES GUI =================
        void refreshVersions() {
            // Clear old list
            versionModel.clear();

            // Add all versions
            for (int i = 0; i < versions.size(); i++) {
                versionModel.addElement("v" + i + " " + versions.get(i));
            }

            // Auto-select latest version
            versionList.setSelectedIndex(versions.size() - 1);

            // Update detailed view
            updateDisplay();
        }

        // ================= UPDATE DISPLAY - SHOWS DETAILS =================
        void updateDisplay() {
            StringBuilder sb = new StringBuilder();

            sb.append("Vector (size: ").append(current.size()).append(")\n\n");
            sb.append("Full contents: ").append(current).append("\n\n");

            sb.append("All elements:\n");
            for (int i = 0; i < current.size(); i++) {
                sb.append("[").append(i).append("] = ").append(current.get(i)).append("\n");
            }

            displayArea.setText(sb.toString());
        }
    }

    // =========================================================
    // UI COMPONENTS (PRESERVED)
    // =========================================================
    static class RoundButton extends JButton {
        RoundButton(String text) {
            super(text);
            setFocusPainted(false);
            setBackground(new Color(60, 100, 255));
            setForeground(Color.WHITE);
            setBorderPainted(false);
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setPreferredSize(new Dimension(180, 40));
        }
    }

    static class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            GradientPaint gp = new GradientPaint(
                    0, 0, new Color(20, 30, 70),
                    0, getHeight(), new Color(70, 130, 180)
            );
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::new);
    }
}