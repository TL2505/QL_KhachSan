"""Generate the reviewable JMeter draft; never starts a load test."""
from pathlib import Path
import xml.etree.ElementTree as E


def prop(parent, tag, name, value):
    E.SubElement(parent, tag, name=name).text = str(value)


root = E.Element("jmeterTestPlan", version="1.2", properties="5.0", jmeter="5.6.3")
tree = E.SubElement(root, "hashTree")
plan = E.SubElement(tree, "TestPlan", guiclass="TestPlanGui", testclass="TestPlan", testname="Phase 3 ISOLATED STAGING ONLY", enabled="true")
prop(plan, "stringProp", "TestPlan.comments", "DRAFT: review seeded request CSV, JWT lifetime, HMAC and idempotency before running. Never production. No response bodies saved.")
body = E.SubElement(tree, "hashTree")
group = E.SubElement(body, "ThreadGroup", guiclass="ThreadGroupGui", testclass="ThreadGroup", testname="200 CCU / 2h", enabled="true")
prop(group, "stringProp", "ThreadGroup.num_threads", "${__P(threads,200)}")
prop(group, "stringProp", "ThreadGroup.ramp_time", "${__P(ramp,120)}")
prop(group, "boolProp", "ThreadGroup.scheduler", "true")
prop(group, "stringProp", "ThreadGroup.duration", "${__P(duration,7200)}")
prop(group, "stringProp", "ThreadGroup.on_sample_error", "continue")
loop = E.SubElement(group, "elementProp", name="ThreadGroup.main_controller", elementType="LoopController", guiclass="LoopControlPanel", testclass="LoopController", testname="Loop", enabled="true")
prop(loop, "boolProp", "LoopController.continue_forever", "false")
prop(loop, "stringProp", "LoopController.loops", "-1")
requests = E.SubElement(body, "hashTree")
csv = E.SubElement(requests, "CSVDataSet", guiclass="TestBeanGUI", testclass="CSVDataSet", testname="Reviewed synthetic requests", enabled="true")
for name, value in {"filename": "${__P(requests_csv,REQUIRED_REVIEWED_REQUESTS.csv)}", "fileEncoding": "UTF-8", "variableNames": "", "delimiter": ",", "shareMode": "shareMode.all"}.items():
    prop(csv, "stringProp", name, value)
for name, value in {"quotedData": "true", "recycle": "false", "stopThread": "true"}.items():
    prop(csv, "boolProp", name, value)
E.SubElement(requests, "hashTree")
for name, percentage in [("availability",60),("booking",20),("financial",10),("ai",10)]:
    control = E.SubElement(requests, "ThroughputController", guiclass="ThroughputControllerGui", testclass="ThroughputController", testname=f"{name} {percentage}%", enabled="true")
    prop(control, "intProp", "ThroughputController.style", 1)
    prop(control, "boolProp", "ThroughputController.perThread", "false")
    prop(control, "stringProp", "ThroughputController.percentThroughput", percentage)
    branch=E.SubElement(requests,"hashTree")
    sample=E.SubElement(branch,"HTTPSamplerProxy",guiclass="HttpTestSampleGui",testclass="HTTPSamplerProxy",testname=name,enabled="true")
    args=E.SubElement(sample,"elementProp",name="HTTPsampler.Arguments",elementType="Arguments")
    collection=E.SubElement(args,"collectionProp",name="Arguments.arguments")
    arg=E.SubElement(collection,"elementProp",name="",elementType="HTTPArgument")
    prop(arg,"boolProp","HTTPArgument.always_encode","false")
    prop(arg,"stringProp","Argument.value","${"+name+"_body}")
    prop(arg,"stringProp","Argument.metadata","=")
    for key,value in {"HTTPSampler.domain":"${__P(host,REQUIRED_ISOLATED_HOST)}","HTTPSampler.port":"${__P(port,443)}","HTTPSampler.protocol":"${__P(scheme,https)}","HTTPSampler.path":"${"+name+"_path}","HTTPSampler.method":"${"+name+"_method}","HTTPSampler.connect_timeout":"3000","HTTPSampler.response_timeout":"5000"}.items():
        prop(sample,"stringProp",key,value)
    prop(sample,"boolProp","HTTPSampler.postBodyRaw","true")
    children=E.SubElement(branch,"hashTree")
    headers=E.SubElement(children,"HeaderManager",guiclass="HeaderPanel",testclass="HeaderManager",testname="Synthetic credentials",enabled="true")
    items=E.SubElement(headers,"collectionProp",name="HeaderManager.headers")
    for key,value in {"Authorization":"Bearer ${token}","Content-Type":"application/json","Idempotency-Key":"${__UUID()}"}.items():
        item=E.SubElement(items,"elementProp",name="",elementType="Header");prop(item,"stringProp","Header.name",key);prop(item,"stringProp","Header.value",value)
    E.SubElement(children,"hashTree")
E.indent(root)
destination=Path(__file__).resolve().parents[1]/"tests"/"performance"/"200_ccu_hotel_soak_test.jmx"
destination.parent.mkdir(parents=True,exist_ok=True)
E.ElementTree(root).write(destination,encoding="utf-8",xml_declaration=True)
