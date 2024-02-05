import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DateFormatter;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;

import com.fazecast.jSerialComm.SerialPort;


public class MainWindow {
    boolean fileSelected = false;
    final JFileChooser fc = new JFileChooser();
    private final Dimension DEFAULT_BUTTON_SIZE = new Dimension(100, 40);
    private final JTextArea logArea = new JTextArea();

    public MainWindow() {
        JFrame frame = new JFrame();
        frame.setTitle("PIC 16LF1847 programmer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setBounds(200, 200, 500, 500);
        frame.setLayout(new FlowLayout());
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
        mainPanel.setVisible(true);
        frame.add(mainPanel);

        JPanel hexFilePanel = new JPanel();
        hexFilePanel.setLayout(new FlowLayout());
        JButton openFileButton = new JButton("Load hex file");
        JLabel chosenFileLabel = new JLabel("File not chosen yet..");
        openFileButton.setSize(DEFAULT_BUTTON_SIZE);
        openFileButton.addActionListener(e -> {
            fc.setApproveButtonText("Open");
            fc.setApproveButtonToolTipText("Open selected file");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Hex files", "hex");
            fc.setFileFilter(filter);
            int returnVal = fc.showOpenDialog(frame);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                fileSelected = true;
                chosenFileLabel.setText("Selected: "+fc.getSelectedFile().getName());
            } else {
                JOptionPane.showMessageDialog(frame, "Please select proper HEX file",
                        "File not chosen", JOptionPane.ERROR_MESSAGE);
            }
        });
        hexFilePanel.add(openFileButton);
        hexFilePanel.add(chosenFileLabel);
        mainPanel.add(hexFilePanel);

        SerialPort[] comPorts = SerialPort.getCommPorts();
        ArrayList<String> choices = new ArrayList<>();
        for (SerialPort port : comPorts) {
            choices.add(port.getDescriptivePortName());
        }


        JPanel comPortPanel = new JPanel();
        comPortPanel.setLayout(new FlowLayout());
        final JComboBox<Object> comPortsComboBox = new JComboBox<>(choices.toArray());
        comPortsComboBox.setVisible(true);
        comPortPanel.add(comPortsComboBox);
        JButton testArduinoButton = new JButton("Test arduino");
        testArduinoButton.addActionListener(e -> {
            log("Start testing arduino...");
        });
        comPortPanel.add(testArduinoButton);
        JButton testPicButton = new JButton("Test PIC");
        testPicButton.addActionListener(e -> {
            log("Start testing PIC...");
        });
        comPortPanel.add(testPicButton);
        mainPanel.add(comPortPanel);


        JPanel sendPanel = new JPanel();
        sendPanel.setLayout(new FlowLayout());
        JButton sendButton = new JButton("Start programming");
        sendButton.setSize(DEFAULT_BUTTON_SIZE);
        sendPanel.add(sendButton);
        mainPanel.add(sendPanel);

        setUpLogArea(mainPanel);

        frame.setVisible(true);
    }

    private void setUpLogArea(JPanel parent){
        JPanel labelPanel = new JPanel();
        labelPanel.setSize(parent.getSize().width,20);
        BoxLayout labelPanelLayout = new BoxLayout(labelPanel,BoxLayout.LINE_AXIS);
        labelPanelLayout.minimumLayoutSize(labelPanel);
        labelPanel.setLayout(labelPanelLayout);
        labelPanel.add(new JLabel("Application log: "));
        parent.add(labelPanel);

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setWheelScrollingEnabled(true);
        logScrollPane.setVerticalScrollBar(logScrollPane.createVerticalScrollBar());
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        logScrollPane.setPreferredSize(new Dimension(480,300));
        parent.add(logScrollPane);
    }

    private void log(String text){
        logArea.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
        logArea.append(": ");
        logArea.append(text);
        logArea.append("\n");
    }
}  