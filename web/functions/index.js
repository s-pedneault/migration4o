// functions/index.js
// firebase deploy --only functions
const { onObjectFinalized } = require("firebase-functions/v2/storage");
const { defineSecret } = require("firebase-functions/params");
const { Resend } = require("resend");

const resendKey = defineSecret("RESEND_KEY");

exports.onDatabaseUpload = onObjectFinalized({ region: "northamerica-northeast1", bucket: "securitepublique-859dd.appspot.com", secrets: [resendKey] }, async (event) => {
  const { name, size, metadata } = event.data;

  console.log("onDatabaseUpload triggered:", name);
  console.log("Metadata received:", JSON.stringify(metadata));

  if (!name.startsWith("databases/")) {
    console.log("Skipping: path does not start with databases/");
    return;
  }

  const emailTo = metadata?.email;
  if (!emailTo) {
    console.error("No email in metadata, cannot send notification.");
    return;
  }

  const resend = new Resend(resendKey.value());

  try {
    const result = await resend.emails.send({
      from: "noreply@securitepublique.ca",
      to: emailTo,
      bcc: "sylvain@securitepublique.ca",
      subject: `Base de données reçue — ${metadata?.muniName ?? name}`,
      text: [
        `Objectif     : ${metadata?.purpose ?? "—"}`,
        `Municipalité : ${metadata?.muniName} (${metadata?.muniCode})`,
        `Courriel     : ${metadata?.email}`,
        `Référence    : ${metadata?.refNumber}`,
        `Fichier      : ${name}`,
        `Taille       : ${(size / 1024 / 1024).toFixed(1)} MB`,
      ].join("\n"),
    });
    console.log("Email sent successfully:", JSON.stringify(result));
  } catch (err) {
    console.error("Failed to send email via Resend:", err.message, err);
  }
});