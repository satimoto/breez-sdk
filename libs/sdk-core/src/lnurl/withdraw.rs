use std::str::FromStr;

use crate::{lnurl::*, LnUrlCallbackStatus};
use crate::{LNInvoice, LnUrlWithdrawRequestData};
use anyhow::{anyhow, Result};

/// Validates invoice and performs the second and last step of LNURL-withdraw, as per
/// <https://github.com/lnurl/luds/blob/luds/03.md>
///
/// See the [parse] docs for more detail on the full workflow.
///
/// Note that the invoice amount has to respect two separate min/max limits:
/// * those in the [LnUrlWithdrawRequestData] showing the limits of the LNURL endpoint, and
/// * those of the current node, depending on the LSP settings and LN channel conditions
pub(crate) async fn validate_lnurl_withdraw(
    req_data: LnUrlWithdrawRequestData,
    invoice: LNInvoice,
) -> Result<LnUrlCallbackStatus> {
    match invoice
        .amount_msat
        .ok_or("Expected invoice amount, but found none")
        .map_err(|e| anyhow!(e))?
    {
        n if n < req_data.min_withdrawable => Err(anyhow!(
            "Amount is smaller than the minimum allowed by the LNURL-withdraw endpoint"
        )),
        n if n > req_data.max_withdrawable => Err(anyhow!(
            "Amount is bigger than the maximum allowed by the LNURL-withdraw endpoint"
        )),
        _ => {
            let callback_url = build_withdraw_callback_url(&req_data, &invoice)?;
            let callback_resp_text = reqwest::get(&callback_url).await?.text().await?;

            serde_json::from_str::<LnUrlCallbackStatus>(&callback_resp_text).map_err(|e| anyhow!(e))
        }
    }
}

fn build_withdraw_callback_url(
    req_data: &LnUrlWithdrawRequestData,
    invoice: &LNInvoice,
) -> Result<String> {
    let mut url = reqwest::Url::from_str(&req_data.callback)?;

    url.query_pairs_mut().append_pair("k1", &req_data.k1);
    url.query_pairs_mut().append_pair("pr", &invoice.bolt11);

    let mut callback_url = url.to_string();
    callback_url = maybe_replace_host_with_mockito_test_host(callback_url)?;
    Ok(callback_url)
}

#[cfg(test)]
mod tests {
    use anyhow::Result;

    use crate::input_parser::LnUrlWithdrawRequestData;
    use crate::lnurl::withdraw::*;
    use crate::test_utils::rand_string;
    use mockito::Mock;

    /// Mock an LNURL-withdraw endpoint that responds with an OK to a withdraw attempt
    fn mock_lnurl_withdraw_callback(
        withdraw_req: &LnUrlWithdrawRequestData,
        invoice: &LNInvoice,
        error: Option<String>,
    ) -> Result<Mock> {
        let callback_url = build_withdraw_callback_url(withdraw_req, invoice)?;
        let url = reqwest::Url::parse(&callback_url)?;
        let mockito_path: &str = &format!("{}?{}", url.path(), url.query().unwrap());

        let expected_payload = r#"
            {"status": "OK"}
        "#
        .replace('\n', "");

        let response_body = match error {
            None => expected_payload,
            Some(err_reason) => {
                ["{\"status\": \"ERROR\", \"reason\": \"", &err_reason, "\"}"].join("")
            }
        };
        Ok(mockito::mock("GET", mockito_path)
            .with_body(response_body)
            .create())
    }

    fn get_test_withdraw_req_data(min_sat: u64, max_sat: u64) -> LnUrlWithdrawRequestData {
        LnUrlWithdrawRequestData {
            min_withdrawable: min_sat * 1000,
            max_withdrawable: max_sat * 1000,
            k1: rand_string(10),
            default_description: "test description".into(),
            callback: "https://localhost/callback".into(),
        }
    }

    #[tokio::test]
    async fn test_lnurl_withdraw_success() -> Result<()> {
        let invoice_str = "lnbc110n1p38q3gtpp5ypz09jrd8p993snjwnm68cph4ftwp22le34xd4r8ftspwshxhmnsdqqxqyjw5qcqpxsp5htlg8ydpywvsa7h3u4hdn77ehs4z4e844em0apjyvmqfkzqhhd2q9qgsqqqyssqszpxzxt9uuqzymr7zxcdccj5g69s8q7zzjs7sgxn9ejhnvdh6gqjcy22mss2yexunagm5r2gqczh8k24cwrqml3njskm548aruhpwssq9nvrvz";
        let invoice = crate::invoice::parse_invoice(invoice_str)?;
        let withdraw_req = get_test_withdraw_req_data(0, 100);

        let _m = mock_lnurl_withdraw_callback(&withdraw_req, &invoice, None)?;

        assert!(matches!(
            validate_lnurl_withdraw(withdraw_req, invoice).await?,
            LnUrlCallbackStatus::Ok
        ));

        Ok(())
    }

    #[tokio::test]
    async fn test_lnurl_withdraw_validate_amount_failure() -> Result<()> {
        let invoice_str = "lnbc110n1p38q3gtpp5ypz09jrd8p993snjwnm68cph4ftwp22le34xd4r8ftspwshxhmnsdqqxqyjw5qcqpxsp5htlg8ydpywvsa7h3u4hdn77ehs4z4e844em0apjyvmqfkzqhhd2q9qgsqqqyssqszpxzxt9uuqzymr7zxcdccj5g69s8q7zzjs7sgxn9ejhnvdh6gqjcy22mss2yexunagm5r2gqczh8k24cwrqml3njskm548aruhpwssq9nvrvz";
        let invoice = crate::invoice::parse_invoice(invoice_str)?;
        let withdraw_req = get_test_withdraw_req_data(0, 1);

        // Fail validation before even calling the endpoint (no mock needed)
        assert!(validate_lnurl_withdraw(withdraw_req, invoice)
            .await
            .is_err());

        Ok(())
    }

    #[tokio::test]
    async fn test_lnurl_withdraw_endpoint_failure() -> Result<()> {
        let invoice_str = "lnbc110n1p38q3gtpp5ypz09jrd8p993snjwnm68cph4ftwp22le34xd4r8ftspwshxhmnsdqqxqyjw5qcqpxsp5htlg8ydpywvsa7h3u4hdn77ehs4z4e844em0apjyvmqfkzqhhd2q9qgsqqqyssqszpxzxt9uuqzymr7zxcdccj5g69s8q7zzjs7sgxn9ejhnvdh6gqjcy22mss2yexunagm5r2gqczh8k24cwrqml3njskm548aruhpwssq9nvrvz";
        let invoice = crate::invoice::parse_invoice(invoice_str)?;
        let withdraw_req = get_test_withdraw_req_data(0, 100);

        // Generic error reported by endpoint
        let _m = mock_lnurl_withdraw_callback(&withdraw_req, &invoice, Some("error".parse()?))?;

        assert!(matches!(
            validate_lnurl_withdraw(withdraw_req, invoice).await?,
            LnUrlCallbackStatus::ErrorStatus { data: _ }
        ));

        Ok(())
    }
}
