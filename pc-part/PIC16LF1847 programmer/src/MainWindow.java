import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Scanner;

import com.fazecast.jSerialComm.SerialPort;


public class MainWindow {
    private final Dimension DEFAULT_BUTTON_SIZE = new Dimension(100, 40);
    private final JTextArea logArea = new JTextArea();
    private final ArrayList<HexLine> hexProgramCommands = new ArrayList<>();
    private final JFrame frame = new JFrame();
    private SerialPort port;
    private final DeviceConfiguration deviceConfiguration = new DeviceConfiguration();

    private JComboBox<Object> comPortsComboBox;
    public MainWindow() {
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
            final JFileChooser fc = new JFileChooser();
            fc.setApproveButtonText("Open");
            fc.setApproveButtonToolTipText("Open selected file");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Hex files", "hex");
            fc.setFileFilter(filter);
            int returnVal = fc.showOpenDialog(frame);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                log("Opening file: "+fc.getSelectedFile().getName());
                chosenFileLabel.setText("Selected: "+fc.getSelectedFile().getName());
                LoadFile(fc.getSelectedFile());
            } else {
               showError("Please select proper HEX file");
            }
        });
        hexFilePanel.add(openFileButton);
        hexFilePanel.add(chosenFileLabel);
        mainPanel.add(hexFilePanel);

        JPanel comPortPanel = getJPanelWithComPorts();
        mainPanel.add(comPortPanel);


        JPanel sendPanel = new JPanel();
        sendPanel.setLayout(new FlowLayout());
        JButton sendButton = new JButton("Start programming");
        sendButton.addActionListener( e -> {
            if(port == null){
                showErrorNoPort();
                return;
            }
            startProgramming();
        });
        sendButton.setSize(DEFAULT_BUTTON_SIZE);
        sendPanel.add(sendButton);
        mainPanel.add(sendPanel);

        setUpLogArea(mainPanel);

        frame.setVisible(true);
    }

    private void startProgramming() {
        if(hexProgramCommands.isEmpty()){
            showError("Please select hex file first.");
            return;
        }
        if(deviceConfiguration.DeviceID.isBlank()){
            showError("Hex file does not contain Device ID. Proceeding without this check.");
        }else{
            //Device ID check here
        }
        JDialog progressDialog = new JDialog(frame,"Programming");
        progressDialog.setBounds(frame.getX()+frame.getWidth()/2-150,frame.getY()+frame.getHeight()/2-10,0,0);
        progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JProgressBar progressBar= new JProgressBar(0, hexProgramCommands.size());
        progressBar.setPreferredSize(new Dimension(300,20));
        progressBar.setStringPainted(true);
        progressDialog.add(progressBar);
        progressDialog.pack();
        progressDialog.setVisible(true);
        Thread programmingThread = new Thread(){
            @Override
            public void run() {
                super.run();
                frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                for(int i=0; i<hexProgramCommands.size();i++){
                    HexLine line = hexProgramCommands.get(i);
                    progressBar.setValue(i+1);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if(!sendLine(line)){
                        break;
                    }
                    if(!verifyResponse(line)){
                        break;
                    }

                }
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                progressDialog.dispose();
                JOptionPane.showMessageDialog(frame, "Programming complete.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        };
        programmingThread.start();

    }

    private boolean verifyResponse(HexLine line) {
        byte[] result = new byte[1024];
        int numRead = port.readBytes(result, result.length);
        log("Read " + numRead + " bytes.");
        String response = new String(result, StandardCharsets.UTF_8);
        if(!response.equals(line.getRest())){
            showError("Something went wrong!\n Error received from Arduino:\n"+response);
            return false;
        }
        return true;
    }

    private boolean sendLine(HexLine line) {
        String command="p";
        command += line.getSize();
        command += line.getAddress();
        command += line.getType();
        command += line.getRest();
        log("Sending command: "+command);
        int writeResult = port.writeBytes(command.getBytes(),command.length());
        log("Send result: "+writeResult);
        if(writeResult!=command.length()){
            String error;
            if(writeResult==-1){
                error="Cannot send any data to Arduino!";
            }else{
                error="Tried to send "+command.length()+" bytes of data but only "+writeResult+" has been sent";
            }
            showError(error);
            return false;
        }
        return true;
    }

    private JPanel getJPanelWithComPorts() {
        log("Detecting serial ports.");
        SerialPort[] comPorts = SerialPort.getCommPorts();
        ArrayList<String> choices = new ArrayList<>();
        for (SerialPort port : comPorts) {
            choices.add(port.getDescriptivePortName());
        }
        if(choices.isEmpty()){
            showError("No COM ports detected. Please connect Arduino and restart program");
        }else{
            openPort(choices.getFirst());
        }
        comPortsComboBox = new JComboBox<>(choices.toArray());
        comPortsComboBox.addItemListener(e -> {
            if(e.getStateChange()==ItemEvent.SELECTED){
                String selected = e.getItem().toString();
                openPort(selected);
            }
        });
        //comPortsComboBox.setSelectedIndex(0);
        JPanel comPortPanel = new JPanel();
        comPortPanel.setLayout(new FlowLayout());
        comPortsComboBox.setVisible(true);
        comPortPanel.add(comPortsComboBox);
        JButton testArduinoButton = new JButton("Test arduino");
        testArduinoButton.addActionListener(e -> {
            log("Start testing arduino at port: " + comPortsComboBox.getSelectedItem());
            testArduino();
        });
        comPortPanel.add(testArduinoButton);
        JButton testPicButton = new JButton("Test PIC");
        testPicButton.addActionListener(e -> {
            log("Start testing PIC...");
            testPic();
        });
        comPortPanel.add(testPicButton);
        return comPortPanel;
    }

    private void testPic() {
        if(port == null) {
            showErrorNoPort();
            return;
        }
        byte[] command = {'i'};
        int writeResult = port.writeBytes(command,1);
        log("Write result: "+writeResult);
        byte[] result = new byte[400];
        int numRead = port.readBytes(result, result.length);
        log("Read " + numRead + " bytes.");
        String s = new String(result, StandardCharsets.UTF_8);
        log("Result of testing PIC: "+s);
    }

    private void showErrorNoPort(){
        showError("No COM port connected. Connect Arduino and restart program");
    }
    private void showError(String content){
        JOptionPane.showMessageDialog(frame, content,
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void testArduino() {
        if(port == null) {
            showErrorNoPort();
            return;
        }
        byte[] command = {(byte)0x68};
        int writeResult = port.writeBytes(command,1);
        log("Write result: "+writeResult);
        byte[] result = new byte[43];
        int numRead = port.readBytes(result, result.length);
        log("Read " + numRead + " bytes.");
        String s = new String(result, StandardCharsets.UTF_8);
        log("Result of testing Arduino: "+s);
    }

    private void LoadFile(File file) {
        hexProgramCommands.clear();
        try {
            Scanner myReader = new Scanner(file);
            while (myReader.hasNextLine()) {
                hexProgramCommands.add(new HexLine(myReader.nextLine()));
            }
            //finding and fetching lines which may contain configuration
            ArrayList<HexLine> linesWithConfiguration = new ArrayList<>();
            for(int i=0;i<hexProgramCommands.size();i++){
                if(!"04".equals(hexProgramCommands.get(i).getType())&&!"02".equals(hexProgramCommands.get(i).getType())){
                    continue;
                }
                if(("04".equals(hexProgramCommands.get(i).getType())&&"0001F9".equals(hexProgramCommands.get(i).getRest()))
                    || "02".equals(hexProgramCommands.get(i).getType())&&"1000EC".equals(hexProgramCommands.get(i).getRest())){
                    int j = 1;
                    while(Integer.parseInt(hexProgramCommands.get(i+j).getAddress(),16)<22){
                        linesWithConfiguration.add(hexProgramCommands.get(i+j));
                        j++;
                    }
                    break;
                }
            }
            //extracting values from found lines
            for(HexLine line : linesWithConfiguration){
                for(int i=0;i<Integer.parseInt(line.getSize(),16);i++){
                    switch (Integer.parseInt(line.getAddress(),16)+i){
                        case 0:
                            deviceConfiguration.UserID1=line.getRest().substring(i*2,4+i*2);
                            break;
                        case 2:
                            deviceConfiguration.UserID2=line.getRest().substring(i*2,4+i*2);
                            break;
                        case 4:
                            deviceConfiguration.UserID3=line.getRest().substring(i*2,4+i*2);
                            break;
                        case 6:
                            deviceConfiguration.UserID4=line.getRest().substring(i*2,4+i*2);
                            break;
                        case 12:
                            deviceConfiguration.DeviceID=line.getRest().substring(i*2,4+i*2);
                            break;
                        case 14:
                            deviceConfiguration.Configuration1=line.getRest().substring(i*2,4+i*2);
                            break;
                        case 16:
                            deviceConfiguration.Configuration2=line.getRest().substring(i*2,4+i*2);
                            break;
                    }
                }
            }
            String content = "Configuration bytes from hex file: \n";
            content+="UserID1: 0x"+(deviceConfiguration.UserID1.isBlank()?"<MISSING!>": deviceConfiguration.UserID1)+"\n";
            content+="UserID2: 0x"+(deviceConfiguration.UserID2.isBlank()?"<MISSING!>": deviceConfiguration.UserID2)+"\n";
            content+="UserID3: 0x"+(deviceConfiguration.UserID3.isBlank()?"<MISSING!>": deviceConfiguration.UserID3)+"\n";
            content+="UserID4: 0x"+(deviceConfiguration.UserID4.isBlank()?"<MISSING!>": deviceConfiguration.UserID4)+"\n";
            content+="DeviceID: 0x"+(deviceConfiguration.DeviceID.isBlank()?"<MISSING!>": deviceConfiguration.DeviceID)+"\n";
            content+="Configuration1: 0x"+(deviceConfiguration.Configuration1.isBlank()?"<MISSING!>": deviceConfiguration.Configuration1)+"\n";
            content+="Configuration2: 0x"+(deviceConfiguration.Configuration2.isBlank()?"<MISSING!>": deviceConfiguration.Configuration2)+"\n";
            if(!deviceConfiguration.isComplete()){
                content+= "\n WARNING: hex file does not contain full configuration or target device ID.\nIt may lead to unstable or not working device.";
            }
            JOptionPane.showMessageDialog(frame, content,
                    "Error", JOptionPane.INFORMATION_MESSAGE);
        } catch (FileNotFoundException ex) {
            hexProgramCommands.clear();
            showError("File is not existing or do not have access to it.");
        }
        log("Hex file opened and loaded successfully.");
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
        logArea.setCaretPosition(logArea.getText().length());
    }

    private void openPort(String selected){
        int startIndex = selected.lastIndexOf("(")+1;
        int endIndex = selected.lastIndexOf(")");
        String com = selected.substring(startIndex,endIndex);
        log("Opening com port: "+com);
        if (port != null){
            port.closePort();
        }
        port = SerialPort.getCommPort(com);
        port.setBaudRate(57600);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 100, 0);
        port.openPort();
        try {
            Thread.sleep(400);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        port.writeBytes(new byte[]{'0'},1);
        try {
            Thread.sleep(400);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
}  