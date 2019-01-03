const wdio = require("webdriverio");

const opts = {
  port: 4723,
  capabilities: {
    platformName: "Android",
    platformVersion: "7.1.1",
    deviceName: "Android Emulator",
    app: "for-appium.apk",
    automationName: "UiAutomator2"
  }
};

const client = wdio.remote(opts);

const elementId = client.findElement("accessibility id","TextField1");
client.elementSendKeys(elementId.ELEMENT, "Hello World!");
const elementValue = client.findElement("accessibility id","TextField1");

client.getElementAttribute(elementValue.ELEMENT,"value").then((attr) => {
  assert.equal(attr,"Hello World!");
});