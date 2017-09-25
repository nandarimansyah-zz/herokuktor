package com.nanda.herokuktor

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.soap.Node
import kotlin.collections.HashMap

private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

fun Application.module() {
    install(DefaultHeaders)
    install(ConditionalHeaders)
    install(PartialContentSupport)

    install(Routing) {
        get("/") {
            call.respond("Hello World")
        }

        post("/") {
            val reqBody: String = call.request.receive()

            val map = requestBodyToMap(reqBody)

            call.response.header("content-type", "text/xml; charset=UTF-8")

            val methodName = map["methodName"]!!.trim()
            val phoneNumber = map["NOHP"]!!.trim()

            if ("topUpRequest".equals(methodName, true) && phoneNumber!!.endsWith("1234")) {
                call.response.status(HttpStatusCode.fromValue(408)!!)
                call.respond("")
            } else {
                call.respond(buildResponse(methodName, map))
            }

        }
    }

}

private fun requestBodyToMap(reqBody: String): HashMap<String, String> {
    val map = hashMapOf<String, String>()
    val inputSource = InputSource(StringReader(reqBody))
    val document = documentBuilder.parse(inputSource)
    document.documentElement.normalize()

    val methodName = document.getElementsByTagName("methodName").item(0).textContent
    map["methodName"] = methodName

    val nodeList = document.getElementsByTagName("member")
    for (i in 0..nodeList.length - 1) {
        val node = nodeList.item(i)

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            val element = node as Element
            val key = element.getElementsByTagName("name").item(0).textContent
            val value = element.getElementsByTagName("value").item(0).textContent

            map[key] = value
        }
    }

    return map
}

private fun buildResponse(methodName : String, requestMap: Map<String, String>): String {
    return if ("topUpInquiry".equals(methodName, true)) {
        buildInquiryResponse(requestMap)
    } else if ("topUpRequest".equals(methodName, true)) {
        buildTransactionResponse(requestMap)
    } else {
        ""
    }
}

fun buildTransactionResponse(requestMap: Map<String, String>): String {
    val phoneNumber = requestMap["NOHP"]!!.trim()
    val requestId = requestMap["REQUESTID"]!!.trim()
    val product = requestMap["NOM"]!!.trim()

    val responseCode = getTransactionResponseCode(phoneNumber)

    return """<?xml version="1.0" encoding="iso-8859-1"?>
<methodResponse>
	<params>
		<param>
			<value>
				<struct>
					<member>
						<name>RESPONSECODE</name>
						<value>
							<string>${responseCode}</string>
						</value>
					</member>
					<member>
						<name>REQUESTID</name>
						<value>
							<string>${requestId}</string>
						</value>
					</member>
					<member>
						<name>MESSAGE</name>
						<value>
							<string>SN=6929445962645983875;ISI ${product} KE ${phoneNumber}, BERHASIL.SAL=4.715.663,ID=401525296,SN=6929445962645983875; TRANSAKSI LANCAR, TRIMS</string>
						</value>
					</member>
					<member>
						<name>SN</name>
						<value>
							<string>6929445962645983875</string>
						</value>
					</member>
					<member>
						<name>TRANSACTIONID</name>
						<value>
							<string>401525296</string>
						</value>
					</member>
				</struct>
			</value>
		</param>
	</params>
</methodResponse>"""
}

fun getTransactionResponseCode(phoneNumber: String?): String {
    return if (phoneNumber!!.endsWith("68") || phoneNumber!!.endsWith("97") || phoneNumber!!.endsWith("92")) {
        "68"
    } else if (phoneNumber!!.endsWith("99")) {
        "99"
    } else {
        "00"
    }
}

fun buildInquiryResponse(requestMap: Map<String, String>): String {
    val phoneNumber = requestMap["NOHP"]!!.trim()
    val responseCode = getInquiryResponseCode(phoneNumber)

    return """<?xml version="1.0" encoding="iso-8859-1"?>
<methodResponse>
	<params>
		<param>
			<value>
				<struct>
					<member>
						<name>RESPONSECODE</name>
						<value>
							<string>${responseCode}</string>
						</value>
					</member>
					<member>
						<name>MESSAGE</name>
						<value>
							<string>ISI ANYPRODUCT KE ${phoneNumber}, BERHASIL.SAL=5.909.263,ID=401490229,SN=6929445962645983875;</string>
						</value>
					</member>
				</struct>
			</value>
		</param>
	</params>
</methodResponse>"""
}

fun getInquiryResponseCode(phoneNumber: String?): String {
    return if (phoneNumber!!.endsWith("97")) {
        "99"
    } else if (phoneNumber!!.endsWith("92") || phoneNumber!!.endsWith("1234")) {
        "92"
    } else {
        "00"
    }
}

fun main(args: Array<String>) {
    val port = Integer.valueOf(System.getenv("PORT"))
    embeddedServer(Netty, port, reloadPackages = listOf("heroku"), module = Application::module).start()
}